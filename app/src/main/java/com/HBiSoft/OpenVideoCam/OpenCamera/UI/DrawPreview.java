package com.HBiSoft.OpenVideoCam.OpenCamera.UI;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import com.HBiSoft.OpenVideoCam.OpenCamera.CameraController.CameraController;
import com.HBiSoft.OpenVideoCam.OpenCamera.MyApplicationInterface;
import com.HBiSoft.OpenVideoCam.OpenCamera.MyDebug;
import com.HBiSoft.OpenVideoCam.OpenCamera.PreferenceKeys;
import com.HBiSoft.OpenVideoCam.OpenCamera.Preview.Preview;
import com.HBiSoft.OpenVideoCam.R;
import com.HBiSoft.OpenVideoCam.OpenCamera.camView;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;

public class DrawPreview {
    private static final String TAG = "DrawPreview";

    private final camView main_activity;
    private final MyApplicationInterface applicationInterface;

    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private final SharedPreferences sharedPreferences;

    // cached preferences (need to call updateSettings() to refresh):
    private boolean has_settings;
    private MyApplicationInterface.PhotoMode photoMode;
    private boolean show_time_pref;
    private boolean show_free_memory_pref;
    private boolean show_iso_pref;
    private boolean show_video_max_amp_pref;
    private boolean show_zoom_pref;
    private boolean show_battery_pref;
    private boolean show_angle_pref;
    private int angle_highlight_color_pref;
    private boolean show_geo_direction_pref;
    private boolean take_photo_border_pref;
    private boolean preview_size_wysiwyg_pref;
    private boolean store_location_pref;
    private boolean show_angle_line_pref;
    private boolean show_pitch_lines_pref;
    private boolean show_geo_direction_lines_pref;
    private boolean immersive_mode_everything_pref;
    private boolean is_raw_pref; // whether in RAW+JPEG or RAW only mode
    private boolean is_raw_only_pref; // whether in RAW only mode
    private boolean is_face_detection_pref;
    private boolean is_audio_enabled_pref;
    private boolean is_high_speed;
    private float capture_rate_factor;
    private boolean auto_stabilise_pref;
    private String preference_grid_pref;
    private String ghost_image_pref;
    private String ghost_selected_image_pref = "";
    private Bitmap ghost_selected_image_bitmap;

    // avoid doing things that allocate memory every frame!
    private final Paint p = new Paint();
    private final RectF draw_rect = new RectF();
    private final int[] gui_location = new int[2];
    private final static DecimalFormat decimalFormat = new DecimalFormat("#0.0");
    private final float scale;
    private final float stroke_width; // stroke_width used for various UI elements
    private Calendar calendar;
    private final DateFormat dateFormatTimeInstance = DateFormat.getTimeInstance();
    private final String ybounds_text;
    // cached Rects for drawTextWithBackground() calls
    private Rect text_bounds_time;
    private Rect text_bounds_free_memory;
    private Rect text_bounds_angle_single;
    private Rect text_bounds_angle_double;

    private final static double close_level_angle = 1.0f;
    private String angle_string; // cached for UI performance
    private double cached_angle; // the angle that we used for the cached angle_string
    private long last_angle_string_time;

    private float free_memory_gb = -1.0f;
    private String free_memory_gb_string;
    private long last_free_memory_time;

    private String current_time_string;
    private long last_current_time_time;

    private String iso_exposure_string;
    private long last_iso_exposure_time;

    private final IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private boolean has_battery_frac;
    private float battery_frac;
    private long last_battery_time;

    private boolean has_video_max_amp;
    private int video_max_amp;
    private long last_video_max_amp_time;
    private int video_max_amp_prev2;
    private int video_max_amp_peak;


    private final Rect icon_dest = new Rect();
    private long needs_flash_time = -1; // time when flash symbol comes on (used for fade-in effect)


    private boolean show_last_image; // whether to show the last image as part of "pause preview"
    private final RectF last_image_src_rect = new RectF();
    private final RectF last_image_dst_rect = new RectF();
    private final Matrix last_image_matrix = new Matrix();
    private boolean allow_ghost_last_image; // whether to allow ghosting the last image

    private long ae_started_scanning_ms = -1; // time when ae started scanning

    private boolean taking_picture; // true iff camera is in process of capturing a picture (including any necessary prior steps such as autofocus, flash/precapture)
    private boolean capture_started; // true iff the camera is capturing
    private boolean front_screen_flash; // true iff the front screen display should maximise to simulate flash

    private boolean continuous_focus_moving;
    private long continuous_focus_moving_ms;


    public DrawPreview(camView main_activity, MyApplicationInterface applicationInterface) {
        if (MyDebug.LOG)
            Log.d(TAG, "DrawPreview");
        this.main_activity = main_activity;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        this.applicationInterface = applicationInterface;
        // n.b., don't call updateSettings() here, as it may rely on things that aren't yet initialise (e.g., the preview)
        // see testHDRRestart

        p.setAntiAlias(true);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setStrokeCap(Paint.Cap.ROUND);
        scale = getContext().getResources().getDisplayMetrics().density;
        this.stroke_width = (1.0f * scale + 0.5f); // convert dps to pixels
        // don't set stroke_width now - set it when we use STROKE style (as it'll be overridden by drawTextWithBackground())


        ybounds_text = getContext().getResources().getString(R.string.zoom) + getContext().getResources().getString(R.string.angle) + getContext().getResources().getString(R.string.direction);
    }

    public void onDestroy() {
        if (MyDebug.LOG)
            Log.d(TAG, "onDestroy");


        if (ghost_selected_image_bitmap != null) {
            ghost_selected_image_bitmap.recycle();
            ghost_selected_image_bitmap = null;
        }
        ghost_selected_image_pref = "";
    }

    private Context getContext() {
        return main_activity;
    }


    public void clearLastImage() {
        if (MyDebug.LOG)
            Log.d(TAG, "clearLastImage");
        this.show_last_image = false;
    }

    public void clearGhostImage() {
        if (MyDebug.LOG)
            Log.d(TAG, "clearGhostImage");
        this.allow_ghost_last_image = false;
    }

    public void cameraInOperation(boolean in_operation) {
        if (in_operation && !main_activity.getPreview().isVideo()) {
            taking_picture = true;
        } else {
            taking_picture = false;
            front_screen_flash = false;
            capture_started = false;
        }
    }

    public void turnFrontScreenFlashOn() {
        if (MyDebug.LOG)
            Log.d(TAG, "turnFrontScreenFlashOn");
        front_screen_flash = true;
    }

    public void onCaptureStarted() {
        if (MyDebug.LOG)
            Log.d(TAG, "onCaptureStarted");
        capture_started = true;
    }

    public void onContinuousFocusMove(boolean start) {
        if (MyDebug.LOG)
            Log.d(TAG, "onContinuousFocusMove: " + start);
        if (start) {
            if (!continuous_focus_moving) { // don't restart the animation if already in motion
                continuous_focus_moving = true;
                continuous_focus_moving_ms = System.currentTimeMillis();
            }
        }
        // if we receive start==false, we don't stop the animation - let it continue
    }

    public void clearContinuousFocusMove() {
        if (MyDebug.LOG)
            Log.d(TAG, "clearContinuousFocusMove");
        if (continuous_focus_moving) {
            continuous_focus_moving = false;
            continuous_focus_moving_ms = 0;
        }
    }


    /**
     * For performance reasons, some of the SharedPreferences settings are cached. This method
     * should be used when the settings may have changed.
     */
    public void updateSettings() {

    }


    private void drawGrids(Canvas canvas) {

    }

    private void drawCropGuides(Canvas canvas) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        if (preview.isVideo() || preview_size_wysiwyg_pref) {
            String preference_crop_guide = sharedPreferences.getString(PreferenceKeys.ShowCropGuidePreferenceKey, "crop_guide_none");
            if (camera_controller != null && preview.getTargetRatio() > 0.0 && !preference_crop_guide.equals("crop_guide_none")) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke_width);
                p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
                double crop_ratio = -1.0;
                switch (preference_crop_guide) {
                    case "crop_guide_1":
                        crop_ratio = 1.0;
                        break;
                    case "crop_guide_1.25":
                        crop_ratio = 1.25;
                        break;
                    case "crop_guide_1.33":
                        crop_ratio = 1.33333333;
                        break;
                    case "crop_guide_1.4":
                        crop_ratio = 1.4;
                        break;
                    case "crop_guide_1.5":
                        crop_ratio = 1.5;
                        break;
                    case "crop_guide_1.78":
                        crop_ratio = 1.77777778;
                        break;
                    case "crop_guide_1.85":
                        crop_ratio = 1.85;
                        break;
                    case "crop_guide_2":
                        crop_ratio = 2.0;
                        break;
                    case "crop_guide_2.33":
                        crop_ratio = 2.33333333;
                        break;
                    case "crop_guide_2.35":
                        crop_ratio = 2.35006120; // actually 1920:817
                        break;
                    case "crop_guide_2.4":
                        crop_ratio = 2.4;
                        break;
                }
                if (crop_ratio > 0.0 && Math.abs(preview.getTargetRatio() - crop_ratio) > 1.0e-5) {
		    		/*if( MyDebug.LOG ) {
		    			Log.d(TAG, "crop_ratio: " + crop_ratio);
		    			Log.d(TAG, "preview_targetRatio: " + preview_targetRatio);
		    			Log.d(TAG, "canvas width: " + canvas.getWidth());
		    			Log.d(TAG, "canvas height: " + canvas.getHeight());
		    		}*/
                    int left = 1, top = 1, right = canvas.getWidth() - 1, bottom = canvas.getHeight() - 1;
                    if (crop_ratio > preview.getTargetRatio()) {
                        // crop ratio is wider, so we have to crop top/bottom
                        double new_hheight = ((double) canvas.getWidth()) / (2.0f * crop_ratio);
                        top = (canvas.getHeight() / 2 - (int) new_hheight);
                        bottom = (canvas.getHeight() / 2 + (int) new_hheight);
                    } else {
                        // crop ratio is taller, so we have to crop left/right
                        double new_hwidth = (((double) canvas.getHeight()) * crop_ratio) / 2.0f;
                        left = (canvas.getWidth() / 2 - (int) new_hwidth);
                        right = (canvas.getWidth() / 2 + (int) new_hwidth);
                    }
                    canvas.drawRect(left, top, right, bottom, p);
                }
                p.setStyle(Paint.Style.FILL); // reset
            }
        }
    }


    /**
     * Formats the level_angle double into a string.
     * Beware of calling this too often - shouldn't be every frame due to performance of DecimalFormat
     * (see http://stackoverflow.com/questions/8553672/a-faster-alternative-to-decimalformat-format ).
     */
    public static String formatLevelAngle(double level_angle) {
        String number_string = decimalFormat.format(level_angle);
        if (Math.abs(level_angle) < 0.1) {
            // avoids displaying "-0.0", see http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
            // only do this when level_angle is small, to help performance
            number_string = number_string.replaceAll("^-(?=0(.0*)?$)", "");
        }
        return number_string;
    }

    /**
     * This includes drawing of the UI that requires the canvas to be rotated according to the preview's
     * current UI rotation.
     */


    private void drawAngleLines(Canvas canvas) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        boolean has_level_angle = preview.hasLevelAngle();
        if (camera_controller != null && !preview.isPreviewPaused() && has_level_angle && (show_angle_line_pref || show_pitch_lines_pref || show_geo_direction_lines_pref)) {
            int ui_rotation = preview.getUIRotation();
            double level_angle = preview.getLevelAngle();
            boolean has_pitch_angle = preview.hasPitchAngle();
            double pitch_angle = preview.getPitchAngle();

            // n.b., must draw this without the standard canvas rotation
            int radius_dps = (ui_rotation == 90 || ui_rotation == 270) ? 60 : 80;
            int radius = (int) (radius_dps * scale + 0.5f); // convert dps to pixels
            double angle = -preview.getOrigLevelAngle();
            // see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
            int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    angle -= 90.0;
                    break;
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                default:
                    break;
            }
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "orig_level_angle: " + preview.getOrigLevelAngle());
				Log.d(TAG, "angle: " + angle);
			}*/
            int cx = canvas.getWidth() / 2;
            int cy = canvas.getHeight() / 2;

            boolean is_level = false;
            if (has_level_angle && Math.abs(level_angle) <= close_level_angle) { // n.b., use level_angle, not angle or orig_level_angle
                is_level = true;
            }

            if (is_level) {
                radius = (int) (radius * 1.2);
            }

            canvas.save();
            canvas.rotate((float) angle, cx, cy);

            final int line_alpha = 160;
            float hthickness = (0.5f * scale + 0.5f); // convert dps to pixels
            p.setStyle(Paint.Style.FILL);
            if (show_angle_line_pref && preview.hasLevelAngleStable()) {
                // only show the angle line if level angle "stable" (i.e., not pointing near vertically up or down)
                // draw outline
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                draw_rect.set(cx - radius - hthickness, cy - 2 * hthickness, cx + radius + hthickness, cy + 2 * hthickness);
                canvas.drawRoundRect(draw_rect, 2 * hthickness, 2 * hthickness, p);
                // draw the vertical crossbar
                draw_rect.set(cx - 2 * hthickness, cy - radius / 2 - hthickness, cx + 2 * hthickness, cy + radius / 2 + hthickness);
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                // draw inner portion
                if (is_level) {
                    p.setColor(angle_highlight_color_pref);
                } else {
                    p.setColor(Color.WHITE);
                }
                p.setAlpha(line_alpha);
                draw_rect.set(cx - radius, cy - hthickness, cx + radius, cy + hthickness);
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

                // draw the vertical crossbar
                draw_rect.set(cx - hthickness, cy - radius / 2, cx + hthickness, cy + radius / 2);
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

                if (is_level) {
                    // draw a second line

                    p.setColor(Color.BLACK);
                    p.setAlpha(64);
                    draw_rect.set(cx - radius - hthickness, cy - 7 * hthickness, cx + radius + hthickness, cy - 3 * hthickness);
                    canvas.drawRoundRect(draw_rect, 2 * hthickness, 2 * hthickness, p);

                    p.setColor(angle_highlight_color_pref);
                    p.setAlpha(line_alpha);
                    draw_rect.set(cx - radius, cy - 6 * hthickness, cx + radius, cy - 4 * hthickness);
                    canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                }
            }
            float camera_angle_x = preview.getViewAngleX();
            float camera_angle_y = preview.getViewAngleY();
            float angle_scale_x = (float) (canvas.getWidth() / (2.0 * Math.tan(Math.toRadians((camera_angle_x / 2.0)))));
            float angle_scale_y = (float) (canvas.getHeight() / (2.0 * Math.tan(Math.toRadians((camera_angle_y / 2.0)))));

            float angle_scale = (float) Math.sqrt(angle_scale_x * angle_scale_x + angle_scale_y * angle_scale_y);
            angle_scale *= preview.getZoomRatio();
            if (has_pitch_angle && show_pitch_lines_pref) {
                int pitch_radius_dps = (ui_rotation == 90 || ui_rotation == 270) ? 100 : 80;
                int pitch_radius = (int) (pitch_radius_dps * scale + 0.5f); // convert dps to pixels
                int angle_step = 10;
                if (preview.getZoomRatio() >= 2.0f)
                    angle_step = 5;
                for (int latitude_angle = -90; latitude_angle <= 90; latitude_angle += angle_step) {
                    double this_angle = pitch_angle - latitude_angle;
                    if (Math.abs(this_angle) < 90.0) {
                        float pitch_distance = angle_scale * (float) Math.tan(Math.toRadians(this_angle)); // angle_scale is already in pixels rather than dps
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "pitch_angle: " + pitch_angle);
							Log.d(TAG, "pitch_distance_dp: " + pitch_distance_dp);
						}*/
                        // draw outline
                        p.setColor(Color.BLACK);
                        p.setAlpha(64);
                        // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                        draw_rect.set(cx - pitch_radius - hthickness, cy + pitch_distance - 2 * hthickness, cx + pitch_radius + hthickness, cy + pitch_distance + 2 * hthickness);
                        canvas.drawRoundRect(draw_rect, 2 * hthickness, 2 * hthickness, p);
                        // draw inner portion
                        p.setColor(Color.WHITE);
                        p.setTextAlign(Paint.Align.LEFT);
                        if (latitude_angle == 0 && Math.abs(pitch_angle) < 1.0) {
                            p.setAlpha(255);
                        } else if (latitude_angle == 90 && Math.abs(pitch_angle - 90) < 3.0) {
                            p.setAlpha(255);
                        } else if (latitude_angle == -90 && Math.abs(pitch_angle + 90) < 3.0) {
                            p.setAlpha(255);
                        } else {
                            p.setAlpha(line_alpha);
                        }
                        draw_rect.set(cx - pitch_radius, cy + pitch_distance - hthickness, cx + pitch_radius, cy + pitch_distance + hthickness);
                        canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                        // draw pitch angle indicator
                        applicationInterface.drawTextWithBackground(canvas, p, "" + latitude_angle + "\u00B0", p.getColor(), Color.BLACK, (int) (cx + pitch_radius + 4 * hthickness), (int) (cy + pitch_distance - 2 * hthickness), MyApplicationInterface.Alignment.ALIGNMENT_CENTRE);
                    }
                }
            }
			/*if( has_geo_direction && has_pitch_angle && show_geo_direction_lines_pref ) {
				int geo_radius_dps = (ui_rotation == 90 || ui_rotation == 270) ? 80 : 100;
				int geo_radius = (int) (geo_radius_dps * scale + 0.5f); // convert dps to pixels
				float geo_angle = (float)Math.toDegrees(geo_direction);
				int angle_step = 10;
				if( preview.getZoomRatio() >= 2.0f )
					angle_step = 5;
				for(int longitude_angle=0;longitude_angle<360;longitude_angle+=angle_step) {
					double this_angle = longitude_angle - geo_angle;
					*//*if( MyDebug.LOG ) {
						Log.d(TAG, "longitude_angle: " + longitude_angle);
						Log.d(TAG, "geo_angle: " + geo_angle);
						Log.d(TAG, "this_angle: " + this_angle);
					}*//*
					// normalise to be in interval [0, 360)
					while( this_angle >= 360.0 )
						this_angle -= 360.0;
					while( this_angle < -360.0 )
						this_angle += 360.0;
					// pick shortest angle
					if( this_angle > 180.0 )
						this_angle = - (360.0 - this_angle);
					if( Math.abs(this_angle) < 90.0 ) {
						*//*if( MyDebug.LOG ) {
							Log.d(TAG, "this_angle is now: " + this_angle);
						}*//*
						float geo_distance = angle_scale * (float)Math.tan( Math.toRadians(this_angle) ); // angle_scale is already in pixels rather than dps
						// draw outline
						p.setColor(Color.BLACK);
						p.setAlpha(64);
						// can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
						draw_rect.set(cx + geo_distance - 2*hthickness, cy - geo_radius - hthickness, cx + geo_distance + 2*hthickness, cy + geo_radius + hthickness);
						canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
						// draw inner portion
						p.setColor(Color.WHITE);
						p.setTextAlign(Paint.Align.CENTER);
						p.setAlpha(line_alpha);
						draw_rect.set(cx + geo_distance - hthickness, cy - geo_radius, cx + geo_distance + hthickness, cy + geo_radius);
						canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
						// draw geo direction angle indicator
						applicationInterface.drawTextWithBackground(canvas, p, "" + longitude_angle + "\u00B0", p.getColor(), Color.BLACK, (int)(cx + geo_distance), (int)(cy - geo_radius - 4*hthickness), MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM);
					}
				}
			}*/

            p.setAlpha(255);
            p.setStyle(Paint.Style.FILL); // reset

            canvas.restore();
        }
    }


    private void doFocusAnimation(Canvas canvas, long time_ms) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        if (camera_controller != null && continuous_focus_moving && !taking_picture) {
            // we don't display the continuous focusing animation when taking a photo - and can also give the impression of having
            // frozen if we pause because the image saver queue is full
            long dt = time_ms - continuous_focus_moving_ms;
            final long length = 1000;
			/*if( MyDebug.LOG )
				Log.d(TAG, "continuous focus moving, dt: " + dt);*/
            if (dt <= length) {
                float frac = ((float) dt) / (float) length;
                float pos_x = canvas.getWidth() / 2.0f;
                float pos_y = canvas.getHeight() / 2.0f;
                float min_radius = (40 * scale + 0.5f); // convert dps to pixels
                float max_radius = (60 * scale + 0.5f); // convert dps to pixels
                float radius;
                if (frac < 0.5f) {
                    float alpha = frac * 2.0f;
                    radius = (1.0f - alpha) * min_radius + alpha * max_radius;
                } else {
                    float alpha = (frac - 0.5f) * 2.0f;
                    radius = (1.0f - alpha) * max_radius + alpha * min_radius;
                }

                p.setColor(Color.WHITE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke_width);
                canvas.drawCircle(pos_x, pos_y, radius, p);
                p.setStyle(Paint.Style.FILL); // reset
            } else {
                clearContinuousFocusMove();
            }
        }

        if (preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure()) {
            long time_since_focus_started = preview.timeSinceStartedAutoFocus();
            float min_radius = (40 * scale + 0.5f); // convert dps to pixels
            float max_radius = (45 * scale + 0.5f); // convert dps to pixels
            float radius = min_radius;
            if (time_since_focus_started > 0) {
                final long length = 500;
                float frac = ((float) time_since_focus_started) / (float) length;
                if (frac > 1.0f)
                    frac = 1.0f;
                if (frac < 0.5f) {
                    float alpha = frac * 2.0f;
                    radius = (1.0f - alpha) * min_radius + alpha * max_radius;
                } else {
                    float alpha = (frac - 0.5f) * 2.0f;
                    radius = (1.0f - alpha) * max_radius + alpha * min_radius;
                }
            }
            int size = (int) radius;

            //SET FOCUS COLOUR

            if (preview.isFocusRecentSuccess())
                p.setColor(Color.rgb(20, 185, 231)); // Green A400
            else if (preview.isFocusRecentFailure())
                p.setColor(Color.rgb(244, 67, 54)); // Red 500
            else
                p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            int pos_x;
            int pos_y;
            if (preview.hasFocusArea()) {
                Pair<Integer, Integer> focus_pos = preview.getFocusPos();
                pos_x = focus_pos.first;
                pos_y = focus_pos.second;
            } else {
                pos_x = canvas.getWidth() / 2;
                pos_y = canvas.getHeight() / 2;
            }
            float frac = 0.5f;
            // horizontal strokes
            canvas.drawLine(pos_x - size, pos_y - size, pos_x - frac * size, pos_y - size, p);
            canvas.drawLine(pos_x + frac * size, pos_y - size, pos_x + size, pos_y - size, p);
            canvas.drawLine(pos_x - size, pos_y + size, pos_x - frac * size, pos_y + size, p);
            canvas.drawLine(pos_x + frac * size, pos_y + size, pos_x + size, pos_y + size, p);
            // vertical strokes
            canvas.drawLine(pos_x - size, pos_y - size, pos_x - size, pos_y - frac * size, p);
            canvas.drawLine(pos_x - size, pos_y + frac * size, pos_x - size, pos_y + size, p);
            canvas.drawLine(pos_x + size, pos_y - size, pos_x + size, pos_y - frac * size, p);
            canvas.drawLine(pos_x + size, pos_y + frac * size, pos_x + size, pos_y + size, p);
            p.setStyle(Paint.Style.FILL); // reset
        }
    }

    public void onDrawPreview(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "onDrawPreview");*/
        if (!has_settings) {
            if (MyDebug.LOG)
                Log.d(TAG, "onDrawPreview: need to update settings");
            updateSettings();
        }
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        int ui_rotation = preview.getUIRotation();

        final long time_ms = System.currentTimeMillis();

        // see documentation for CameraController.shouldCoverPreview()
        if (preview.usingCamera2API() && (camera_controller == null || camera_controller.shouldCoverPreview())) {
            p.setColor(Color.BLACK);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
        }

        if (camera_controller != null && front_screen_flash) {
            p.setColor(Color.WHITE);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
        } else if ("flash_frontscreen_torch".equals(preview.getCurrentFlashValue())) { // getCurrentFlashValue() may return null
            p.setColor(Color.WHITE);
            p.setAlpha(200); // set alpha so user can still see some of the preview
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
            p.setAlpha(255);
        }


        preview.getView().getLocationOnScreen(gui_location);
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "gui_location[0]: " + gui_location[0]);
			Log.d(TAG, "gui_location[1]: " + gui_location[1]);
		}*/

        if (camera_controller != null && taking_picture && !front_screen_flash && take_photo_border_pref) {
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            float this_stroke_width = (5.0f * scale + 0.5f); // convert dps to pixels
            p.setStrokeWidth(this_stroke_width);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
            p.setStyle(Paint.Style.FILL); // reset
            p.setStrokeWidth(stroke_width); // reset
        }
        drawGrids(canvas);

        drawCropGuides(canvas);

        if (camera_controller != null && ghost_selected_image_bitmap != null) {
            setLastImageMatrix(canvas, ghost_selected_image_bitmap, ui_rotation, true);
            p.setAlpha(127);
            canvas.drawBitmap(ghost_selected_image_bitmap, last_image_matrix, p);
            p.setAlpha(255);
        }


        //drawUI(canvas, time_ms);

        drawAngleLines(canvas);

        doFocusAnimation(canvas, time_ms);

        CameraController.Face[] faces_detected = preview.getFacesDetected();
        if (faces_detected != null) {
            p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            for (CameraController.Face face : faces_detected) {
                // Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
                if (face.score >= 50) {
                    canvas.drawRect(face.rect, p);
                }
            }
            p.setStyle(Paint.Style.FILL); // reset
        }


    }

    private void setLastImageMatrix(Canvas canvas, Bitmap bitmap, int this_ui_rotation, boolean flip_front) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        last_image_src_rect.left = 0;
        last_image_src_rect.top = 0;
        last_image_src_rect.right = bitmap.getWidth();
        last_image_src_rect.bottom = bitmap.getHeight();
        if (this_ui_rotation == 90 || this_ui_rotation == 270) {
            last_image_src_rect.right = bitmap.getHeight();
            last_image_src_rect.bottom = bitmap.getWidth();
        }
        last_image_dst_rect.left = 0;
        last_image_dst_rect.top = 0;
        last_image_dst_rect.right = canvas.getWidth();
        last_image_dst_rect.bottom = canvas.getHeight();
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "thumbnail: " + bitmap.getWidth() + " x " + bitmap.getHeight());
			Log.d(TAG, "canvas: " + canvas.getWidth() + " x " + canvas.getHeight());
		}*/
        last_image_matrix.setRectToRect(last_image_src_rect, last_image_dst_rect, Matrix.ScaleToFit.CENTER); // use CENTER to preserve aspect ratio
        if (this_ui_rotation == 90 || this_ui_rotation == 270) {
            // the rotation maps (0, 0) to (tw/2 - th/2, th/2 - tw/2), so we translate to undo this
            float diff = bitmap.getHeight() - bitmap.getWidth();
            last_image_matrix.preTranslate(diff / 2.0f, -diff / 2.0f);
        }
        last_image_matrix.preRotate(this_ui_rotation, bitmap.getWidth() / 2.0f, bitmap.getHeight() / 2.0f);
        if (flip_front) {
            boolean is_front_facing = camera_controller != null && camera_controller.isFrontFacing();
            if (is_front_facing && !sharedPreferences.getString(PreferenceKeys.FrontCameraMirrorKey, "preference_front_camera_mirror_no").equals("preference_front_camera_mirror_photo")) {
                last_image_matrix.preScale(-1.0f, 1.0f, bitmap.getWidth() / 2.0f, 0.0f);
            }
        }
    }


    public boolean getStoredAutoStabilisePref() {
        return this.auto_stabilise_pref;
    }
}
