package com.HBiSoft.OpenVideoCam.OpenCamera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageButton;

import com.HBiSoft.OpenVideoCam.OpenCamera.Preview.BasicApplicationInterface;
import com.HBiSoft.OpenVideoCam.OpenCamera.Preview.Preview;
import com.HBiSoft.OpenVideoCam.OpenCamera.Preview.VideoProfile;
import com.HBiSoft.OpenVideoCam.OpenCamera.UI.DrawPreview;
import com.HBiSoft.OpenVideoCam.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface extends BasicApplicationInterface {
    private static final String TAG = "MyApplicationInterface";


    // note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
    public enum PhotoMode {
        Standard,
        DRO, // single image "fake" HDR
        HDR, // HDR created from multiple (expo bracketing) images
        ExpoBracketing, // take multiple expo bracketed images, without combining to a single image
        FocusBracketing, // take multiple focus bracketed images, without combining to a single image
        FastBurst,
        NoiseReduction
    }

    private final camView main_activity;
    private final StorageUtils storageUtils;
    private final DrawPreview drawPreview;


    private File last_video_file = null;
    private Uri last_video_file_saf = null;


    private final Rect text_bounds = new Rect();
    private boolean used_front_screen_flash;

    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private final SharedPreferences sharedPreferences;

    private boolean last_images_saf; // whether the last images array are using SAF or not

    /**
     * This class keeps track of the images saved in this batch, for use with Pause Preview option, so we can share or trash images.
     */
    private static class LastImage {
        final boolean share; // one of the images in the list should have share set to true, to indicate which image to share
        final String name;
        Uri uri;

        LastImage(Uri uri, boolean share) {
            this.name = null;
            this.uri = uri;
            this.share = share;
        }

        LastImage(String filename, boolean share) {
            this.name = filename;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // previous to Android 7, we could just use a "file://" uri, but this is no longer supported on Android 7, and
                // results in a android.os.FileUriExposedException when trying to share!
                // see https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
                // so instead we leave null for now, and set it from MyApplicationInterface.scannedFile().
                this.uri = null;
            } else {
                this.uri = Uri.parse("file://" + this.name);
            }
            this.share = share;
        }
    }

    private final List<LastImage> last_images = new ArrayList<>();

    private final ToastBoxer photo_delete_toast = new ToastBoxer();

    // camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
    private int cameraId = 0;
    // camera properties that aren't saved even in the bundle; these should be initialised/reset in reset()
    private int zoom_factor; // don't save zoom, as doing so tends to confuse users; other camera applications don't seem to save zoom when pause/resuming
    private String nr_mode;

    MyApplicationInterface(camView main_activity, Bundle savedInstanceState) {
        long debug_time = 0;
        if (MyDebug.LOG) {
            Log.d(TAG, "MyApplicationInterface");
            debug_time = System.currentTimeMillis();
        }
        this.main_activity = main_activity;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);

        this.storageUtils = new StorageUtils(main_activity, this);
        if (MyDebug.LOG)
            Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
        this.drawPreview = new DrawPreview(main_activity, this);


        this.reset();
        if (savedInstanceState != null) {
            // load the things we saved in onSaveInstanceState().
            if (MyDebug.LOG)
                Log.d(TAG, "read from savedInstanceState");
            cameraId = savedInstanceState.getInt("cameraId", 0);
            if (MyDebug.LOG)
                Log.d(TAG, "found cameraId: " + cameraId);
        }

        if (MyDebug.LOG)
            Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
    }

    /**
     * Here we save states which aren't saved in preferences (we don't want them to be saved if the
     * application is restarted from scratch), but we do want to preserve if Android has to recreate
     * the application (e.g., configuration change, or it's destroyed while in background).
     */
    void onSaveInstanceState(Bundle state) {
        if (MyDebug.LOG)
            Log.d(TAG, "onSaveInstanceState");
        if (MyDebug.LOG)
            Log.d(TAG, "save cameraId: " + cameraId);
        state.putInt("cameraId", cameraId);
    }

    void onDestroy() {
        if (MyDebug.LOG)
            Log.d(TAG, "onDestroy");
        if (drawPreview != null) {
            drawPreview.onDestroy();
        }

    }


    StorageUtils getStorageUtils() {
        return storageUtils;
    }


    public DrawPreview getDrawPreview() {
        return drawPreview;
    }

    @Override
    public Context getContext() {
        return main_activity;
    }

    @Override
    public boolean useCamera2() {
        if (main_activity.supportsCamera2()) {
            return sharedPreferences.getBoolean(PreferenceKeys.UseCamera2PreferenceKey, false);
        }
        return false;
    }

    @Override
    public int createOutputVideoMethod() {
        String action = main_activity.getIntent().getAction();
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action)) {
            if (MyDebug.LOG)
                Log.d(TAG, "from video capture intent");
            Bundle myExtras = main_activity.getIntent().getExtras();
            if (myExtras != null) {
                Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
                if (intent_uri != null) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "save to: " + intent_uri);
                    return VIDEOMETHOD_URI;
                }
            }
            // if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
            if (MyDebug.LOG)
                Log.d(TAG, "intent uri not specified");
            // note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
            return VIDEOMETHOD_FILE;
        }
        boolean using_saf = storageUtils.isUsingSAF();
        return using_saf ? VIDEOMETHOD_SAF : VIDEOMETHOD_FILE;
    }

    @Override
    public File createOutputVideoFile(String extension) throws IOException {
        last_video_file = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_VIDEO, "", extension, new Date());
        return last_video_file;
    }

    @Override
    public Uri createOutputVideoSAF(String extension) throws IOException {
        last_video_file_saf = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_VIDEO, "", extension, new Date());
        return last_video_file_saf;
    }

    @Override
    public Uri createOutputVideoUri() {
        String action = main_activity.getIntent().getAction();
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action)) {
            if (MyDebug.LOG)
                Log.d(TAG, "from video capture intent");
            Bundle myExtras = main_activity.getIntent().getExtras();
            if (myExtras != null) {
                Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
                if (intent_uri != null) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "save to: " + intent_uri);
                    return intent_uri;
                }
            }
        }
        throw new RuntimeException(); // programming error if we arrived here
    }



    /**
     * Returns whether the current fps preference is one that requires a "high speed" video size/
     * frame rate.
     */
    public boolean fpsIsHighSpeed() {
        return main_activity.getPreview().fpsIsHighSpeed(getVideoFPSPref());
    }



    @Override
    public float getVideoCaptureRateFactor() {
        float capture_rate_factor = sharedPreferences.getFloat(PreferenceKeys.getVideoCaptureRatePreferenceKey(main_activity.getPreview().getCameraId()), 1.0f);
        if (MyDebug.LOG)
            Log.d(TAG, "capture_rate_factor: " + capture_rate_factor);
        if (Math.abs(capture_rate_factor - 1.0f) > 1.0e-5) {
            // check stored capture rate is valid
            if (MyDebug.LOG)
                Log.d(TAG, "check stored capture rate is valid");
            List<Float> supported_capture_rates = getSupportedVideoCaptureRates();
            if (MyDebug.LOG)
                Log.d(TAG, "supported_capture_rates: " + supported_capture_rates);
            boolean found = false;
            for (float this_capture_rate : supported_capture_rates) {
                if (Math.abs(capture_rate_factor - this_capture_rate) < 1.0e-5) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Log.e(TAG, "stored capture_rate_factor: " + capture_rate_factor + " not supported");
                capture_rate_factor = 1.0f;
            }
        }
        return capture_rate_factor;
    }

    /**
     * This will always return 1, even if slow motion isn't supported (i.e.,
     * slow motion should only be considered as supported if at least 2 entries
     * are returned. Entries are returned in increasing order.
     */
    public List<Float> getSupportedVideoCaptureRates() {
        List<Float> rates = new ArrayList<>();
        if (main_activity.getPreview().supportsVideoHighSpeed()) {
            // We consider a slow motion rate supported if we can get at least 30fps in slow motion.
            // If this code is updated, see if we also need to update how slow motion fps is chosen
            // in getVideoFPSPref().
            if (main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(240) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(240)) {
                rates.add(1.0f / 8.0f);
                rates.add(1.0f / 4.0f);
                rates.add(1.0f / 2.0f);
            } else if (main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(120) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(120)) {
                rates.add(1.0f / 4.0f);
                rates.add(1.0f / 2.0f);
            } else if (main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(60) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(60)) {
                rates.add(1.0f / 2.0f);
            }
        }
        rates.add(1.0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // add timelapse options
            // in theory this should work on any Android version, though video fails to record in timelapse mode on Galaxy Nexus...
            rates.add(2.0f);
            rates.add(3.0f);
            rates.add(4.0f);
            rates.add(5.0f);
            rates.add(10.0f);
            rates.add(20.0f);
            rates.add(30.0f);
            rates.add(60.0f);
            rates.add(120.0f);
            rates.add(240.0f);
        }
        return rates;
    }

    @Override
    public boolean useVideoLogProfile() {
        String video_log = sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off");
        // only return true for values recognised by getVideoLogProfileStrength()
        switch (video_log) {
            case "off":
                return false;
            case "fine":
            case "low":
            case "medium":
            case "strong":
            case "extra_strong":
                return true;
        }
        return false;
    }

    @Override
    public float getVideoLogProfileStrength() {
        String video_log = sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off");
        // remember to update useVideoLogProfile() if adding/changing modes
        switch (video_log) {
            case "off":
                return 0.0f;
            case "fine":
                return 1.0f;
            case "low":
                return 5.0f;
            case "medium":
                return 10.0f;
            case "strong":
                return 100.0f;
            case "extra_strong":
                return 500.0f;
        }
        return 0.0f;
    }




    @Override
    public double getCalibratedLevelAngle() {
        return sharedPreferences.getFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f);
    }



    @Override
    public int getBurstNImages() {
        PhotoMode photo_mode = getPhotoMode();
        if (photo_mode == PhotoMode.FastBurst) {
            String n_images_value = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
            int n_images;
            try {
                n_images = Integer.parseInt(n_images_value);
            } catch (NumberFormatException e) {
                if (MyDebug.LOG)
                    Log.e(TAG, "failed to parse FastBurstNImagesPreferenceKey value: " + n_images_value);
                e.printStackTrace();
                n_images = 5;
            }
            return n_images;
        }
        return 1;
    }

    @Override
    public boolean getBurstForNoiseReduction() {
        PhotoMode photo_mode = getPhotoMode();
        return photo_mode == PhotoMode.NoiseReduction;
    }








    /**
     * Returns the current photo mode.
     * Note, this always should return the true photo mode - if we're in video mode and taking a photo snapshot while
     * video recording, the caller should override. We don't override here, as this preference may be used to affect how
     * the CameraController is set up, and we don't always re-setup the camera when switching between photo and video modes.
     */
    public PhotoMode getPhotoMode() {
        String photo_mode_pref = sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
		/*if( MyDebug.LOG )
			Log.d(TAG, "photo_mode_pref: " + photo_mode_pref);*/
        boolean dro = photo_mode_pref.equals("preference_photo_mode_dro");
        if (dro && main_activity.supportsDRO())
            return PhotoMode.DRO;
        boolean hdr = photo_mode_pref.equals("preference_photo_mode_hdr");
        if (hdr && main_activity.supportsHDR())
            return PhotoMode.HDR;
        boolean expo_bracketing = photo_mode_pref.equals("preference_photo_mode_expo_bracketing");
        if (expo_bracketing && main_activity.supportsExpoBracketing())
            return PhotoMode.ExpoBracketing;
        boolean focus_bracketing = photo_mode_pref.equals("preference_photo_mode_focus_bracketing");
        if (focus_bracketing && main_activity.supportsFocusBracketing())
            return PhotoMode.FocusBracketing;
        boolean fast_burst = photo_mode_pref.equals("preference_photo_mode_fast_burst");
        if (fast_burst && main_activity.supportsFastBurst())
            return PhotoMode.FastBurst;
        boolean noise_reduction = photo_mode_pref.equals("preference_photo_mode_noise_reduction");
        if (noise_reduction && main_activity.supportsNoiseReduction())
            return PhotoMode.NoiseReduction;
        return PhotoMode.Standard;
    }




    private static boolean photoModeSupportsRaw(PhotoMode photo_mode) {
        // RAW only supported for Std or DRO modes
        return photo_mode == PhotoMode.Standard || photo_mode == PhotoMode.DRO;
    }



    /**
     * Whether RAW only mode is enabled.
     */
    public boolean isRawOnly() {
        PhotoMode photo_mode = getPhotoMode();
        return isRawOnly(photo_mode);
    }

    /**
     * Use this instead of isRawOnly() if the photo mode is already known - useful to call e.g. from camView.supportsDRO()
     * without causing an infinite loop!
     */
    boolean isRawOnly(PhotoMode photo_mode) {
        if (isImageCaptureIntent())
            return false;
        if (main_activity.getPreview().isVideo())
            return false; // video snapshot mode
        if (photoModeSupportsRaw(photo_mode)) {
            switch (sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no")) {
                case "preference_raw_only":
                    return true;
            }
        }
        return false;
    }


    @Override
    public boolean useCamera2FakeFlash() {
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, false);
    }

    @Override
    public boolean useCamera2FastBurst() {
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, true);
    }

    @Override
    public boolean usePhotoVideoRecording() {
        // we only show the preference for Camera2 API (since there's no point disabling the feature for old API)
        if (!useCamera2())
            return true;
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2PhotoVideoRecordingPreferenceKey, true);
    }

    @Override
    public boolean isTestAlwaysFocus() {
        if (MyDebug.LOG) {
            Log.d(TAG, "isTestAlwaysFocus: " + main_activity.is_test);
        }
        return main_activity.is_test;
    }

    @Override
    public void cameraSetup() {
        main_activity.cameraSetup();
        drawPreview.clearContinuousFocusMove();
        // Need to cause drawPreview.updateSettings(), otherwise icons like HDR won't show after force-restart, because we only
        // know that HDR is supported after the camera is opened
        // Also needed for settings which update when switching between photo and video mode.
        drawPreview.updateSettings();
    }


    //switch camera
    @Override
    public int getCameraIdPref() {
        return cameraId;
    }

    @Override
    public void setCameraIdPref(int cameraId) {
        this.cameraId = cameraId;
    }

    @Override
    public void onContinuousFocusMove(boolean start) {
        if (MyDebug.LOG)
            Log.d(TAG, "onContinuousFocusMove: " + start);
        drawPreview.onContinuousFocusMove(start);
    }


    @Override
    public void touchEvent(MotionEvent event) {
		/*if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}*/
    }


    @Override
    public void startingVideo() {
        ImageButton view = main_activity.findViewById(R.id.take_photo);
        view.setImageResource(R.drawable.record_vid_btn_stop);
        view.setContentDescription(getContext().getResources().getString(R.string.stop_video));
        view.setTag(R.drawable.record_vid_btn_stop); // for testing

        main_activity.startCamButtonAnimation(view);
    }

    //Video Recording Started
    @Override
    public void startedVideo() {


    }

    @Override
    public void stoppingVideo() {

        if (MyDebug.LOG)
            Log.d(TAG, "stoppingVideo()");
        ImageButton view = main_activity.findViewById(R.id.take_photo);
        view.setImageResource(R.drawable.take_video_selector);
        view.setContentDescription(getContext().getResources().getString(R.string.start_video));

        main_activity.stopCamButtonAnimation(view);
    }

    public void fileToDelete(String path){


    }


    @Override
    public void stoppedVideo(final int video_method, final Uri uri, final String filename) {
        if (MyDebug.LOG) {
            Log.d(TAG, "stoppedVideo");
            Log.d(TAG, "video_method " + video_method);
            Log.d(TAG, "uri " + uri);
            Log.d(TAG, "filename " + filename);
        }


        //If file exists
        if (video_method == VIDEOMETHOD_FILE) {
            if (filename != null) {
                //Toast.makeText(main_activity, "File Saved", Toast.LENGTH_SHORT).show();
                Preview preview = main_activity.getPreview();
                //preview.showToast(null, "File Saved");

                main_activity.goToPlayer(filename);

                File file = new File(filename);
                storageUtils.broadcastFile(file, false, true, true);
            }
        } else {
            if (uri != null) {
                File real_file = storageUtils.broadcastUri(uri, false, true, true);
                if (real_file != null) {
                    main_activity.test_last_saved_image = real_file.getAbsolutePath();
                }
            }
        }


    }


    @Override
    public void onVideoInfo(int what, int extra) {
        // we don't show a toast for MEDIA_RECORDER_INFO_MAX_DURATION_REACHED - conflicts with "n repeats to go" toast from Preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && what == MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED) {
            if (MyDebug.LOG)
                Log.d(TAG, "next output file started");
            int message_id = R.string.video_max_filesize;
            main_activity.getPreview().showToast(null, message_id);
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (MyDebug.LOG)
                Log.d(TAG, "max filesize reached");
            int message_id = R.string.video_max_filesize;
            main_activity.getPreview().showToast(null, message_id);
        }
        // in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
        // fixed in 1.25; also was correct for 1.23 and earlier
        String debug_value = "info_" + what + "_" + extra;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("last_video_error", debug_value);
        editor.apply();
    }

    @Override
    public void onFailedStartPreview() {
        main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
    }

    @Override
    public void onCameraError() {
        main_activity.getPreview().showToast(null, R.string.camera_error);
        main_activity.onCameraError();
    }


    @Override
    public void onVideoError(int what, int extra) {
        int message_id = R.string.video_error_unknown;
        if (what == MediaRecorder.MEDIA_ERROR_SERVER_DIED) {
            message_id = R.string.video_error_server_died;
        }
        main_activity.onVideoError(what, extra, message_id);
    }

    @Override
    public void onVideoRecordStartError(VideoProfile profile) {
        String error_message;
        String features = main_activity.getPreview().getErrorFeatures(profile);
        if (features.length() > 0) {
            error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
        } else {
            error_message = getContext().getResources().getString(R.string.failed_to_record_video);
        }

        main_activity.onVideoRecordStartError(error_message);

        //main_activity.getPreview().showToast(null, error_message);
        ImageButton view = main_activity.findViewById(R.id.take_photo);
        view.setImageResource(R.drawable.take_video_selector);
        view.setContentDescription(getContext().getResources().getString(R.string.start_video));
        view.setTag(R.drawable.take_video_selector); // for testing
    }

    @Override
    public void onVideoRecordStopError(VideoProfile profile) {
        String features = main_activity.getPreview().getErrorFeatures(profile);
        String error_message = getContext().getResources().getString(R.string.video_may_be_corrupted);
        if (features.length() > 0) {
            error_message += ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
        }
        //main_activity.getPreview().showToast(null, error_message);

        main_activity.onVideoRecordStopError(error_message);
    }

    @Override
    public void onFailedReconnectError() {
        main_activity.getPreview().showToast(null, R.string.failed_to_reconnect_camera);
    }

    @Override
    public void onFailedCreateVideoFileError() {
        main_activity.getPreview().showToast(null, R.string.failed_to_save_video);
        ImageButton view = main_activity.findViewById(R.id.take_photo);
        view.setImageResource(R.drawable.take_video_selector);
        view.setContentDescription(getContext().getResources().getString(R.string.start_video));
        view.setTag(R.drawable.take_video_selector); // for testing
    }


    @Override
    public void cameraInOperation(boolean in_operation, boolean is_video) {
        if (MyDebug.LOG)
            Log.d(TAG, "cameraInOperation: " + in_operation);
        if (!in_operation && used_front_screen_flash) {
            //main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
            used_front_screen_flash = false;
        }
        drawPreview.cameraInOperation(in_operation);
        main_activity.getMainUI().showGUI(!in_operation, is_video);
    }

    @Override
    public void turnFrontScreenFlashOn() {
        if (MyDebug.LOG)
            Log.d(TAG, "turnFrontScreenFlashOn");
        used_front_screen_flash = true;
        //main_activity.setBrightnessForCamera(true); // ensure we have max screen brightness, even if user preference not set for max brightness
        drawPreview.turnFrontScreenFlashOn();
    }


    @Override
    public void onCaptureStarted() {
        if (MyDebug.LOG)
            Log.d(TAG, "onCaptureStarted");
        drawPreview.onCaptureStarted();
    }


    @Override
    public void cameraClosed() {
        if (MyDebug.LOG)
            Log.d(TAG, "cameraClosed");
        drawPreview.clearContinuousFocusMove();
    }


    @Override
    public void layoutUI() {
        main_activity.getMainUI().layoutUI();
    }



    @Override
    public void requestCameraPermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "requestCameraPermission");
        main_activity.getPermissionHandler().requestCameraPermission();
    }

    @Override
    public boolean needsStoragePermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "needsStoragePermission");
        return true;
    }

    @Override
    public void requestStoragePermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "requestStoragePermission");
        main_activity.getPermissionHandler().requestStoragePermission();
    }

    @Override
    public void requestRecordAudioPermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "requestRecordAudioPermission");
        main_activity.getPermissionHandler().requestRecordAudioPermission();
    }



    /**
     * Should be called to reset parameters which aren't expected to be saved (e.g., resetting zoom when application is paused,
     * when switching between photo/video modes, or switching cameras).
     */
    void reset() {
        if (MyDebug.LOG)
            Log.d(TAG, "reset");
        this.zoom_factor = 0;
        this.nr_mode = "preference_nr_mode_normal";
    }

    @Override
    public void onDrawPreview(Canvas canvas) {
        drawPreview.onDrawPreview(canvas);
    }

    public enum Alignment {
        ALIGNMENT_TOP,
        ALIGNMENT_CENTRE,
        ALIGNMENT_BOTTOM
    }


    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, true);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, shadow, null);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow, Rect bounds) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        paint.setAlpha(64);
        if (bounds != null) {
            text_bounds.set(bounds);
        } else {
            int alt_height = 0;
            if (ybounds_text != null) {
                paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
                alt_height = text_bounds.bottom - text_bounds.top;
            }
            paint.getTextBounds(text, 0, text.length(), text_bounds);
            if (ybounds_text != null) {
                text_bounds.bottom = text_bounds.top + alt_height;
            }
        }
        final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
        if (paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER) {
            float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
            if (paint.getTextAlign() == Paint.Align.CENTER)
                width /= 2.0f;
            text_bounds.left -= width;
            text_bounds.right -= width;
        }
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
        text_bounds.left += location_x - padding;
        text_bounds.right += location_x + padding;
        // unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
        int top_y_diff = -text_bounds.top + padding - 1;
        if (alignment_y == Alignment.ALIGNMENT_TOP) {
            int height = text_bounds.bottom - text_bounds.top + 2 * padding;
            text_bounds.top = location_y - 1;
            text_bounds.bottom = text_bounds.top + height;
            location_y += top_y_diff;
        } else if (alignment_y == Alignment.ALIGNMENT_CENTRE) {
            int height = text_bounds.bottom - text_bounds.top + 2 * padding;
            //int y_diff = - text_bounds.top + padding - 1;
            text_bounds.top = (int) (0.5 * ((location_y - 1) + (text_bounds.top + location_y - padding))); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
            text_bounds.bottom = text_bounds.top + height;
            location_y += (int) (0.5 * top_y_diff); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
        } else {
            text_bounds.top += location_y - padding;
            text_bounds.bottom += location_y + padding;
        }
        paint.setColor(foreground);
        canvas.drawText(text, location_x, location_y, paint);
        if (shadow) {
            paint.setColor(background);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            canvas.drawText(text, location_x, location_y, paint);
            paint.setStyle(Paint.Style.FILL); // set back to default
        }
        return text_bounds.bottom - text_bounds.top;
    }


    boolean isImageCaptureIntent() {
        boolean image_capture_intent = false;
        String action = main_activity.getIntent().getAction();
        if (MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            if (MyDebug.LOG)
                Log.d(TAG, "from image capture intent");
            image_capture_intent = true;
        }
        return image_capture_intent;
    }


    void clearLastImages() {
        if (MyDebug.LOG)
            Log.d(TAG, "clearLastImages");
        last_images_saf = false;
        last_images.clear();
        drawPreview.clearLastImage();
    }



    void scannedFile(File file, Uri uri) {

        // see note under LastImage constructor for why we need to update the Uris
        for (int i = 0; i < last_images.size(); i++) {
            LastImage last_image = last_images.get(i);
            if (MyDebug.LOG)
                Log.d(TAG, "compare to last_image: " + last_image.name);
            if (last_image.uri == null && last_image.name != null && last_image.name.equals(file.getAbsolutePath())) {
                if (MyDebug.LOG)
                    Log.d(TAG, "updated last_image : " + i);
                last_image.uri = uri;
            }
        }
    }


    public boolean test_set_available_memory = false;
    public long test_available_memory = 0;
}
