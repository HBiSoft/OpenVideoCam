package com.HBiSoft.OpenVideoCam.OpenCamera.UI;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.HBiSoft.OpenVideoCam.OpenCamera.MyDebug;
import com.HBiSoft.OpenVideoCam.OpenCamera.PreferenceKeys;
import com.HBiSoft.OpenVideoCam.R;
import com.HBiSoft.OpenVideoCam.OpenCamera.camView;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;


public class MainUI {
    private static final String TAG = "MainUI";

    private final camView main_activity;


    private int current_orientation;

    enum UIPlacement {
        UIPLACEMENT_RIGHT,
        UIPLACEMENT_LEFT,
        UIPLACEMENT_TOP
    }

    private UIPlacement ui_placement = UIPlacement.UIPLACEMENT_RIGHT;
    private int top_margin = 0;
    private boolean view_rotate_animation;

    private boolean immersive_mode;
    private boolean show_gui_photo = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video
    private boolean show_gui_video = true;


    // for testing:
    private final Map<String, View> test_ui_buttons = new Hashtable<>();
    public int test_saved_popup_width;
    public int test_saved_popup_height;

    public MainUI(camView main_activity) {
        if (MyDebug.LOG)
            Log.d(TAG, "MainUI");
        this.main_activity = main_activity;

    }


    /**
     * Similar view.setRotation(ui_rotation), but achieves this via an animation.
     */
    private void setViewRotation(View view, float ui_rotation) {
        if (!view_rotate_animation) {
            view.setRotation(ui_rotation);
        }
        float rotate_by = ui_rotation - view.getRotation();
        if (rotate_by > 181.0f)
            rotate_by -= 360.0f;
        else if (rotate_by < -181.0f)
            rotate_by += 360.0f;
        // view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
        // we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
        view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public void layoutUI() {
        layoutUI(false);
    }

    private UIPlacement computeUIPlacement() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String ui_placement_string = sharedPreferences.getString(PreferenceKeys.UIPlacementPreferenceKey, "ui_right");
        switch (ui_placement_string) {
            case "ui_left":
                return UIPlacement.UIPLACEMENT_LEFT;
            case "ui_top":
                return UIPlacement.UIPLACEMENT_TOP;
            default:
                return UIPlacement.UIPLACEMENT_RIGHT;
        }
    }


    //**Setting and positioning of buttons
    private void layoutUI(boolean popup_container_only) {
        // reset:
        top_margin = 0;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        // we cache the preference_ui_placement to save having to check it in the draw() method
        this.ui_placement = computeUIPlacement();
        if (MyDebug.LOG)
            Log.d(TAG, "ui_placement: " + ui_placement);
        // new code for orientation fixed to landscape
        // the display orientation should be locked to landscape, but how many degrees is that?
        int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                break;
        }
        // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
        // relative_orientation is clockwise from landscape-left
        //int relative_orientation = (current_orientation + 360 - degrees) % 360;
        int relative_orientation = (current_orientation + degrees) % 360;
        if (MyDebug.LOG) {
            Log.d(TAG, "    current_orientation = " + current_orientation);
            Log.d(TAG, "    degrees = " + degrees);
            Log.d(TAG, "    relative_orientation = " + relative_orientation);
        }
        final int ui_rotation = (360 - relative_orientation) % 360;
        main_activity.getPreview().setUIRotation(ui_rotation);
        int align_left = RelativeLayout.ALIGN_LEFT;
        int align_right = RelativeLayout.ALIGN_RIGHT;
        int left_of = RelativeLayout.LEFT_OF;
        int right_of = RelativeLayout.RIGHT_OF;
        int iconpanel_left_of = left_of;
        int iconpanel_right_of = right_of;
        int above = RelativeLayout.ABOVE;
        int below = RelativeLayout.BELOW;
        int iconpanel_above = above;
        int iconpanel_below = below;
        int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
        int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
        int iconpanel_align_parent_left = align_parent_left;
        int iconpanel_align_parent_right = align_parent_right;
        int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
        int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
        int iconpanel_align_parent_top = align_parent_top;
        int iconpanel_align_parent_bottom = align_parent_bottom;
        if (ui_placement == UIPlacement.UIPLACEMENT_LEFT) {
            above = RelativeLayout.BELOW;
            below = RelativeLayout.ABOVE;
            align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
            align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
            iconpanel_align_parent_top = align_parent_top;
            iconpanel_align_parent_bottom = align_parent_bottom;
        } else if (ui_placement == UIPlacement.UIPLACEMENT_TOP) {
            iconpanel_left_of = RelativeLayout.BELOW;
            iconpanel_right_of = RelativeLayout.ABOVE;
            iconpanel_above = RelativeLayout.LEFT_OF;
            iconpanel_below = RelativeLayout.RIGHT_OF;
            iconpanel_align_parent_left = RelativeLayout.ALIGN_PARENT_BOTTOM;
            iconpanel_align_parent_right = RelativeLayout.ALIGN_PARENT_TOP;
            iconpanel_align_parent_top = RelativeLayout.ALIGN_PARENT_LEFT;
            iconpanel_align_parent_bottom = RelativeLayout.ALIGN_PARENT_RIGHT;
        }

        Point display_size = new Point();
        Display display = main_activity.getWindowManager().getDefaultDisplay();
        display.getSize(display_size);
        final int display_height = Math.min(display_size.x, display_size.y);

        if (!popup_container_only) {
            // we use a dummy button, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)
            View view = main_activity.findViewById(R.id.gui_anchor);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(iconpanel_align_parent_left, 0);
            layoutParams.addRule(iconpanel_align_parent_right, RelativeLayout.TRUE);
            layoutParams.addRule(iconpanel_align_parent_top, RelativeLayout.TRUE);
            layoutParams.addRule(iconpanel_align_parent_bottom, 0);
            layoutParams.addRule(iconpanel_above, 0);
            layoutParams.addRule(iconpanel_below, 0);
            layoutParams.addRule(iconpanel_left_of, 0);
            layoutParams.addRule(iconpanel_right_of, 0);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);
            View previous_view = view;

            List<View> buttons_permanent = new ArrayList<>();
            if (ui_placement == UIPlacement.UIPLACEMENT_TOP) {
                // not part of the icon panel in TOP mode
                layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                layoutParams.addRule(align_parent_left, 0);
                layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
                layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
                layoutParams.addRule(align_parent_bottom, 0);
                layoutParams.addRule(above, 0);
                layoutParams.addRule(below, 0);
                layoutParams.addRule(left_of, 0);
                layoutParams.addRule(right_of, 0);
                view.setLayoutParams(layoutParams);
                setViewRotation(view, ui_rotation);
            }


            List<View> buttons_all = new ArrayList<>(buttons_permanent);
            // icons which only sometimes show on the icon panel:


            for (View this_view : buttons_all) {
                layoutParams = (RelativeLayout.LayoutParams) this_view.getLayoutParams();
                layoutParams.addRule(iconpanel_align_parent_left, 0);
                layoutParams.addRule(iconpanel_align_parent_right, 0);
                layoutParams.addRule(iconpanel_align_parent_top, RelativeLayout.TRUE);
                layoutParams.addRule(iconpanel_align_parent_bottom, 0);
                layoutParams.addRule(iconpanel_above, 0);
                layoutParams.addRule(iconpanel_below, 0);
                layoutParams.addRule(iconpanel_left_of, previous_view.getId());
                layoutParams.addRule(iconpanel_right_of, 0);
                this_view.setLayoutParams(layoutParams);
                setViewRotation(this_view, ui_rotation);
                previous_view = this_view;
            }

            int button_size = main_activity.getResources().getDimensionPixelSize(R.dimen.onscreen_button_size);
            if (ui_placement == UIPlacement.UIPLACEMENT_TOP) {
                // need to dynamically lay out the permanent icons

                int count = 0;
                View first_visible_view = null;
                View last_visible_view = null;
                for (View this_view : buttons_permanent) {
                    if (this_view.getVisibility() == View.VISIBLE) {
                        if (first_visible_view == null)
                            first_visible_view = this_view;
                        last_visible_view = this_view;
                        count++;
                    }
                }

                if (count > 0) {

                    int total_button_size = count * button_size;
                    int margin = 0;
                    if (total_button_size > display_height) {
                        button_size = display_height / count;
                    } else {
                        if (count > 1)
                            margin = (display_height - total_button_size) / (count - 1);
                    }
                    for (View this_view : buttons_permanent) {
                        if (this_view.getVisibility() == View.VISIBLE) {
                            //this_view.setPadding(0, margin/2, 0, margin/2);
                            layoutParams = (RelativeLayout.LayoutParams) this_view.getLayoutParams();
                            // be careful if we change how the margins are laid out: it looks nicer when only the settings icon
                            // is displayed (when taking a photo) if it is still shown left-most, rather than centred; also
                            // needed for "pause preview" trash/icons to be shown properly (test by rotating the phone to update
                            // the layout)
                            layoutParams.setMargins(0, this_view == first_visible_view ? 0 : margin / 2, 0, this_view == last_visible_view ? 0 : margin / 2);
                            layoutParams.width = button_size;
                            layoutParams.height = button_size;
                            this_view.setLayoutParams(layoutParams);
                        }
                    }
                    top_margin = button_size;
                }
            } else {
                // need to reset size/margins to their default
                for (View this_view : buttons_permanent) {
                    layoutParams = (RelativeLayout.LayoutParams) this_view.getLayoutParams();
                    layoutParams.setMargins(0, 0, 0, 0);
                    layoutParams.width = button_size;
                    layoutParams.height = button_size;
                    this_view.setLayoutParams(layoutParams);
                }
            }


            //Set rotation to RecordButton
            View camview = main_activity.findViewById(R.id.take_photo);
            setViewRotation(camview, ui_rotation);

            //Set rotation to SwitchCam Button
            View vieww = main_activity.findViewById(R.id.switch_camera);
            setViewRotation(vieww, ui_rotation);


            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_left, 0);
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
            layoutParams.addRule(align_parent_top, 0);
            layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
            view.setLayoutParams(layoutParams);
            view.setRotation(180.0f); // should always match the zoom_seekbar, so that zoom in and out are in the same directions

            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            // if we are showing the zoom control, the align next to that; otherwise have it aligned close to the edge of screen
            if (sharedPreferences.getBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, false)) {
                layoutParams.addRule(align_left, 0);

                layoutParams.addRule(below, 0);
                // need to clear the others, in case we turn zoom controls on/off
                layoutParams.addRule(align_parent_left, 0);
                layoutParams.addRule(align_parent_right, 0);
                layoutParams.addRule(align_parent_top, 0);
                layoutParams.addRule(align_parent_bottom, 0);
            } else {
                layoutParams.addRule(align_parent_left, 0);
                layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
                layoutParams.addRule(align_parent_top, 0);
                layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
                // need to clear the others, in case we turn zoom controls on/off
                layoutParams.addRule(align_left, 0);
                layoutParams.addRule(align_right, 0);
                layoutParams.addRule(above, 0);
                layoutParams.addRule(below, 0);
            }
            view.setLayoutParams(layoutParams);

            view = main_activity.findViewById(R.id.focus_seekbar);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_left, R.id.preview);
            layoutParams.addRule(align_right, 0);
            layoutParams.addRule(right_of, 0);
            layoutParams.addRule(align_parent_top, 0);
            layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
            view.setLayoutParams(layoutParams);

            view = main_activity.findViewById(R.id.focus_bracketing_target_seekbar);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_left, R.id.preview);
            layoutParams.addRule(align_right, 0);
            layoutParams.addRule(right_of, 0);
            layoutParams.addRule(above, R.id.focus_seekbar);
            layoutParams.addRule(below, 0);
            view.setLayoutParams(layoutParams);
        }


        if (!popup_container_only) {
            setVideoIcon();
            // no need to call setSwitchCameraContentDescription()
        }


    }


    public void setVideoIcon() {
        if (main_activity.getPreview() != null) {
            ImageButton view = main_activity.findViewById(R.id.take_photo);
            int resource;
            int content_description;

            if (main_activity.getPreview().isVideoRecording()) {
                resource = R.drawable.record_vid_btn_stop;
            } else {
                resource = R.drawable.record_vid_btn_start;
            }

            content_description = main_activity.getPreview().isVideoRecording() ? R.string.stop_video : R.string.start_video;

            view.setImageResource(resource);
            view.setContentDescription(main_activity.getResources().getString(content_description));

        }
    }

    /**
     * Set content description for switch camera button.
     */
    public void setSwitchCameraContentDescription() {
        if (MyDebug.LOG)
            Log.d(TAG, "setSwitchCameraContentDescription()");
        if (main_activity.getPreview() != null && main_activity.getPreview().canSwitchCamera()) {
            ImageView view = main_activity.findViewById(R.id.switch_camera);
            int content_description;
            int cameraId = main_activity.getNextCameraId();
            if (main_activity.getPreview().getCameraControllerManager().isFrontFacing(cameraId)) {
                content_description = R.string.switch_to_front_camera;
            } else {
                content_description = R.string.switch_to_back_camera;
            }
            if (MyDebug.LOG)
                Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
            view.setContentDescription(main_activity.getResources().getString(content_description));
        }
    }


    public void onOrientationChanged(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
            return;
        int diff = Math.abs(orientation - current_orientation);
        if (diff > 180)
            diff = 360 - diff;
        // only change orientation when sufficiently changed
        if (diff > 60) {
            orientation = (orientation + 45) / 90 * 90;
            orientation = orientation % 360;
            if (orientation != current_orientation) {
                this.current_orientation = orientation;
                if (MyDebug.LOG) {
                    Log.d(TAG, "current_orientation is now: " + current_orientation);
                }
                view_rotate_animation = true;
                layoutUI();
                view_rotate_animation = false;
            }
        }
    }


    public void showGUI(final boolean show, final boolean is_video) {
        if (is_video)
            this.show_gui_video = show;
        else
            this.show_gui_photo = show;
        showGUI();
    }

    private void showGUI() {
        main_activity.runOnUiThread(new Runnable() {
            public void run() {
                final int visibility = (show_gui_photo && show_gui_video) ? View.VISIBLE : View.GONE; // for UI that is hidden while taking photo or video
                View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
                //View switchVideoButton = main_activity.findViewById(R.id.switch_video);


                if (main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1)
                    switchCameraButton.setVisibility(visibility);
                //switchVideoButton.setVisibility(visibility);


                if (show_gui_photo && show_gui_video) {
                    layoutUI(); // needed for "top" UIPlacement, to auto-arrange the buttons
                }
            }
        });
    }


    @SuppressWarnings("deprecation")
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (MyDebug.LOG)
            Log.d(TAG, "onKeyDown: " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: // media codes are for "selfie sticks" buttons
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_FOCUS: {
                // important not to repeatedly request focus, even though main_activity.getPreview().requestAutoFocus() will cancel - causes problem with hardware camera key where a half-press means to focus
                // also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down - see https://sourceforge.net/p/opencamera/tickets/174/ ,
                // or same issue above for volume key focus
                if (event.getDownTime() == event.getEventTime() && !main_activity.getPreview().isFocusWaiting()) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "request focus due to focus key");
                    main_activity.getPreview().requestAutoFocus();
                }
                return true;
            }

        }
        return false;
    }


}
