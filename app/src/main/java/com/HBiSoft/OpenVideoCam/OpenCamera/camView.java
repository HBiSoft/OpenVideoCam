package com.HBiSoft.OpenVideoCam.OpenCamera;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.renderscript.RenderScript;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.HBiSoft.OpenVideoCam.OpenCamera.CameraController.CameraController;
import com.HBiSoft.OpenVideoCam.OpenCamera.CameraController.CameraControllerManager2;
import com.HBiSoft.OpenVideoCam.OpenCamera.Preview.Preview;
import com.HBiSoft.OpenVideoCam.OpenCamera.UI.MainUI;
import com.HBiSoft.OpenVideoCam.OpenCamera.UI.ManualSeekbars;
import com.HBiSoft.OpenVideoCam.R;
import com.HBiSoft.OpenVideoCam.MainActivity;

import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

/**
 * The main Activity for Open Camera.
 */
public class camView extends AppCompatActivity {
    private static final String TAG = "camView";
    private MainUI mainUI;
    private PermissionHandler permissionHandler;
    private ManualSeekbars manualSeekbars;
    private TextFormatter textFormatter;
    private MyApplicationInterface applicationInterface;
    private Preview preview;
    private OrientationEventListener orientationEventListener;
    private int large_heap_memory;
    private boolean supports_auto_stabilise;
    private boolean supports_force_video_4k;
    private boolean supports_camera2;

    private boolean saf_dialog_from_preferences; // if a SAF dialog is opened, this records whether we opened it from the Preferences
    private boolean camera_in_background; // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
    private boolean screen_is_locked; // whether screen is "locked" - this is Open Camera's own lock to guard against accidental presses, not the standard Android lock
    private final Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<>();


    //private final ToastBoxer screen_locked_toast = new ToastBoxer();
    private final ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();

    // for testing; must be volatile for test project reading the state
    public boolean is_test; // whether called from OpenCamera.test testing
    public volatile Bitmap gallery_bitmap;
    public volatile boolean test_low_memory;
    public volatile boolean test_have_angle;
    public volatile float test_angle;
    public volatile String test_last_saved_image;
    public static boolean test_force_supports_camera2;
    public volatile String test_save_settings_file;

    //***Record Video
    View takePhotoButton;


    //dialog
    private int mRotation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // don't show orientation animations
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
            getWindow().setAttributes(layout);
        }

        setContentView(R.layout.activity_cam_view);




        if (getIntent() != null && getIntent().getExtras() != null) {
            // whether called from testing
            is_test = getIntent().getExtras().getBoolean("test_project");

        }


        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // determine whether we should support "auto stabilise" feature
        // risk of running out of memory on lower end devices, due to manipulation of large bitmaps
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        large_heap_memory = activityManager.getLargeMemoryClass();
        if (large_heap_memory >= 128) {
            supports_auto_stabilise = true;
        }


        // hack to rule out phones unlikely to have 4K video, so no point even offering the option!
        // both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL), as does Galaxy K Zoom
        // also added the check for having 128MB standard heap, to support modded LG G2, which has 128MB standard, 256MB large - see https://sourceforge.net/p/opencamera/tickets/9/
        if (activityManager.getMemoryClass() >= 128 || activityManager.getLargeMemoryClass() >= 512) {
            supports_force_video_4k = true;
        }


        // set up components
        permissionHandler = new PermissionHandler(this);
        mainUI = new MainUI(this);
        manualSeekbars = new ManualSeekbars();
        applicationInterface = new MyApplicationInterface(this, savedInstanceState);

        textFormatter = new TextFormatter(this);

        // determine whether we support Camera2 API
        initCamera2Support();

/*		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
			// no point having talkback care about this - and (hopefully) avoid Google Play pre-launch accessibility warnings
			View container = findViewById(R.id.hide_container);
			container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		}*/

        // set up window flags for normal operation
        setWindowFlagsForCamera();


        // set up the camera and its preview
        preview = new Preview(applicationInterface, ((ViewGroup) this.findViewById(R.id.preview)));


        // initialise on-screen button visibility
        View switchCameraButton = findViewById(R.id.switch_camera);
        switchCameraButton.setVisibility(preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE);

        //Pause Video
		/*View pauseVideoButton = findViewById(R.id.pause_video);
		pauseVideoButton.setVisibility(View.GONE);*/

        //Record Video
        takePhotoButton = findViewById(R.id.take_photo);
        takePhotoButton.setVisibility(View.VISIBLE);

        takePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeVideoPressed();

                Log.e("Profile information : ", " fileExtension = "+preview.getVideoProfile().fileExtension + " audioBitRate = "+preview.getVideoProfile().audioBitRate +
                        " audioChannels = "+preview.getVideoProfile().audioChannels +
                        " audioSampleRate = "+preview.getVideoProfile().audioSampleRate + " audioSource = "+preview.getVideoProfile().audioSource +  " fileFormat = "+preview.getVideoProfile().fileFormat +
                        " audioCodec = "+preview.getVideoProfile().audioCodec +   " videoCodec = "+preview.getVideoProfile().videoCodec + " videoBitRate = "+preview.getVideoProfile().videoBitRate +
                        " videoCaptureRate = "+preview.getVideoProfile().videoCaptureRate + " videoSource = "+preview.getVideoProfile().videoSource + " videoFrameRate = "+preview.getVideoProfile().videoFrameRate +
                        " videoFrameWidth = "+preview.getVideoProfile().videoFrameWidth + " videoFrameHeight = "+preview.getVideoProfile().videoFrameHeight);


            }
        });


        // listen for orientation event change
        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                camView.this.mainUI.onOrientationChanged(orientation);


            }
        };


        // show "about" dialog for first time use; also set some per-device defaults
        boolean has_done_first_time = sharedPreferences.contains(PreferenceKeys.FirstTimePreferenceKey);
        if (!has_done_first_time) {
            setDeviceDefaults();
        }


		/*{
			// handle What's New dialog
			int version_code = -1;
			try {
				PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				version_code = pInfo.versionCode;
			}
			catch(PackageManager.NameNotFoundException e) {
				if( MyDebug.LOG )
					Log.d(TAG, "NameNotFoundException exception trying to get version number");
				e.printStackTrace();
			}
			if( version_code != -1 ) {
				int latest_version = sharedPreferences.getInt(PreferenceKeys.LatestVersionPreferenceKey, 0);
				if( MyDebug.LOG ) {
					Log.d(TAG, "version_code: " + version_code);
					Log.d(TAG, "latest_version: " + latest_version);
				}
				//final boolean whats_new_enabled = false;
				final boolean whats_new_enabled = true;
				if( whats_new_enabled ) {
					// whats_new_version is the version code that the What's New text is written for. Normally it will equal the
					// current release (version_code), but it some cases we may want to leave it unchanged.
					// E.g., we have a "What's New" for 1.44 (64), but then push out a quick fix for 1.44.1 (65). We don't want to
					// show the dialog again to people who already received 1.44 (64), but we still want to show the dialog to people
					// upgrading from earlier versions.
					int whats_new_version = 67; // 1.45
					whats_new_version = Math.min(whats_new_version, version_code); // whats_new_version should always be <= version_code, but just in case!
					if( MyDebug.LOG ) {
						Log.d(TAG, "whats_new_version: " + whats_new_version);
					}
					final boolean force_whats_new = false;
					//final boolean force_whats_new = true; // for testing
					boolean allow_show_whats_new = sharedPreferences.getBoolean(PreferenceKeys.ShowWhatsNewPreferenceKey, true);
					if( MyDebug.LOG )
						Log.d(TAG, "allow_show_whats_new: " + allow_show_whats_new);
					// don't show What's New if this is the first time the user has run
					if( has_done_first_time && allow_show_whats_new && ( force_whats_new || whats_new_version > latest_version ) ) {
						AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
						alertDialog.setTitle(R.string.whats_new);
						alertDialog.setMessage(R.string.whats_new_text);
						alertDialog.setPositiveButton(android.R.string.ok, null);
						alertDialog.setNegativeButton(R.string.donate, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if( MyDebug.LOG )
									Log.d(TAG, "donate");
								Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(camView.getDonateLink()));
								startActivity(browserIntent);
							}
						});
						alertDialog.show();
					}
				}
				// We set the latest_version whether or not the dialog is shown - if we showed the first time dialog, we don't
				// want to then show the What's New dialog next time we run! Similarly if the user had disabled showing the dialog,
				// but then enables it, we still shouldn't show the dialog until the new time Open Camera upgrades.
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
				editor.apply();
			}
		}*/

        //setModeFromIntents(savedInstanceState);

        // load icons


    }

    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////
    //////////////// My Methods ////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////

    final Animation anim = new AlphaAnimation(0.0f, 1.0f);




    public void startCamButtonAnimation(View view) {
        anim.setDuration(500);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        takePhotoButton.startAnimation(anim);
    }

    public void stopCamButtonAnimation(View view) {
        //Toast.makeText(this, "called", Toast.LENGTH_SHORT).show();
        takePhotoButton.clearAnimation();

    }


    public void goToPlayer(String filePath) {
        //Intent to go to TestPlayer
        //Toast.makeText(this, "called", Toast.LENGTH_SHORT).show();

        Intent newIntent = new Intent(camView.this, MainActivity.class);
        newIntent.putExtra("success", "success");
        startActivity(newIntent);
        finish();
    }

    //Error handeling

    public void onVideoError(int what, int extra, int message) {

        Intent errorIntent = new Intent(camView.this, CameraErrorActivity.class);
        startActivity(errorIntent);
        overridePendingTransition(0, 0);

    }

    public void onCameraError() {

    }

    public void onVideoRecordStartError(String message) {

    }

    public void onVideoRecordStopError(String message) {

    }

    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////

    //Keep
    void setDeviceDefaults() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        if (is_samsung || is_oneplus) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, true);
            editor.apply();
        }

    }


    /**
     * Determine whether we support Camera2 API.
     */
    private void initCamera2Support() {
        supports_camera2 = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // originally we allowed Camera2 if all cameras support at least LIMITED
            // as of 1.45, we allow Camera2 if at least one camera supports at least LIMITED - this
            // is to support devices that might have a camera with LIMITED or better support, but
            // also a LEGACY camera
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            supports_camera2 = false;
            int n_cameras = manager2.getNumberOfCameras();
            if (n_cameras == 0) {
                supports_camera2 = false;
            }
            for (int i = 0; i < n_cameras && !supports_camera2; i++) {
                if (manager2.allowCamera2Support(i)) {
                    supports_camera2 = true;
                }
            }
        }

        //test_force_supports_camera2 = true; // test
        if (test_force_supports_camera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                supports_camera2 = true;
            }
        }

    }


    @Override
    protected void onDestroy() {
        preview.onDestroy();
        if (applicationInterface != null) {
            applicationInterface.onDestroy();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // see note in HDRProcessor.onDestroy() - but from Android M, renderscript contexts are released with releaseAllContexts()
            // doc for releaseAllContexts() says "If no contexts have been created this function does nothing"
            RenderScript.releaseAllContexts();
        }
        // Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
        for (Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
            entry.getValue().recycle();
        }
        preloaded_bitmap_resources.clear();

        super.onDestroy();
    }


    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = mainUI.onKeyDown(keyCode, event);
        if (handled)
            return true;
        return super.onKeyDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {

        return super.onKeyUp(keyCode, event);
    }


    @Override
    protected void onResume() {

        super.onResume();

        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the MyPreferenceFragment settings
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);

        //mSensorManager.registerListener(accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        orientationEventListener.enable();


        mainUI.layoutUI();


        applicationInterface.reset(); // should be called before opening the camera in preview.onResume()

        preview.onResume();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

    }

    @Override
    protected void onPause() {
        super.onPause(); // docs say to call this before freeing other things
        orientationEventListener.disable();
        applicationInterface.clearLastImages(); // this should happen when pausing the preview, but call explicitly just to be safe
        applicationInterface.getDrawPreview().clearGhostImage();
        preview.onPause();

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
        // needed if app is paused/resumed when settings is open and device is in portrait mode
        preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }


    void takeVideoPressed() {
        this.preview.takeVideoPressed();
    }

    public int getNextCameraId() {
        int cameraId = preview.getCameraId();
        if (this.preview.canSwitchCamera()) {
            int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
            cameraId = (cameraId + 1) % n_cameras;
        }
        return cameraId;
    }

    public void clickedSwitchCamera(View view) {
        if (MyDebug.LOG)
            Log.d(TAG, "clickedSwitchCamera");
        if (preview.isOpeningCamera()) {
            if (MyDebug.LOG)
                Log.d(TAG, "already opening camera in background thread");
            return;
        }
        if (this.preview.canSwitchCamera()) {
            int cameraId = getNextCameraId();
            View switchCameraButton = findViewById(R.id.switch_camera);
            switchCameraButton.setEnabled(false); // prevent slowdown if user repeatedly clicks
            applicationInterface.reset();
            this.preview.setCamera(cameraId);
            switchCameraButton.setEnabled(true);
            // no need to call mainUI.setSwitchCameraContentDescription - this will be called from PreviewcameraSetup when the
            // new camera is opened
        }
    }


    @Override
    public void onBackPressed() {
        if (preview != null && preview.isPreviewPaused()) {
            preview.startCameraPreview();
            return;
        }

        super.onBackPressed();
    }


    /**
     * Sets the window flags for normal operation (when camera preview is visible).
     */
    public void setWindowFlagsForCamera() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // force to landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        camera_in_background = false;

    }


    @Override
    protected void onSaveInstanceState(Bundle state) {

        super.onSaveInstanceState(state);
        if (this.preview != null) {
            preview.onSaveInstanceState(state);
        }
        if (this.applicationInterface != null) {
            applicationInterface.onSaveInstanceState(state);
        }
    }


    void cameraSetup() {
        if (this.supportsForceVideo4K() && preview.usingCamera2API()) {
            this.disableForceVideo4K();
        }
        if (this.supportsForceVideo4K() && preview.getVideoQualityHander().getSupportedVideoSizes() != null) {
            for (CameraController.Size size : preview.getVideoQualityHander().getSupportedVideoSizes()) {
                if (size.width >= 3840 && size.height >= 2160) {
                    this.disableForceVideo4K();
                }
            }
        }

        setManualFocusSeekbar(false);
        setManualFocusSeekbar(true);

        mainUI.setVideoIcon();
        mainUI.setSwitchCameraContentDescription();

    }

    private void setManualFocusSeekbar(final boolean is_target_distance) {
        final SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        focusSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
        ManualSeekbars.setProgressSeekbarScaled(focusSeekBar, 0.0, preview.getMinimumFocusDistance(), is_target_distance ? preview.getCameraController().getFocusBracketingTargetDistance() : preview.getCameraController().getFocusDistance());
        focusSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            private boolean has_saved_zoom;
            private int saved_zoom_factor;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double frac = progress / (double) focusSeekBar.getMax();
                double scaling = ManualSeekbars.seekbarScaling(frac);
                float focus_distance = (float) (scaling * preview.getMinimumFocusDistance());
                preview.setFocusDistance(focus_distance, is_target_distance);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                has_saved_zoom = false;

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (has_saved_zoom) {
                    preview.getCameraController().setZoom(saved_zoom_factor);
                }
                preview.stoppedSettingFocusDistance(is_target_distance);
            }
        });
        setManualFocusSeekBarVisibility(is_target_distance);
    }

    void setManualFocusSeekBarVisibility(final boolean is_target_distance) {
        SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        boolean is_visible = preview.getCurrentFocusValue() != null && this.getPreview().getCurrentFocusValue().equals("focus_mode_manual2");
        if (is_target_distance) {
            is_visible = is_visible && (applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.FocusBracketing) && !preview.isVideo();
        }
        final int visibility = is_visible ? View.VISIBLE : View.GONE;
        focusSeekBar.setVisibility(visibility);
    }



    public boolean supportsDRO() {
        if (applicationInterface.isRawOnly(MyApplicationInterface.PhotoMode.DRO))
            return false; // if not saving JPEGs, no point having DRO mode, as it won't affect the RAW images
        // require at least Android 5, for the Renderscript support in HDRProcessor
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    }

    public boolean supportsHDR() {
        // we also require the device have sufficient memory to do the processing
        // also require at least Android 5, for the Renderscript support in HDRProcessor
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && large_heap_memory >= 128 && preview.supportsExpoBracketing());
    }

    public boolean supportsExpoBracketing() {
        if (applicationInterface.isImageCaptureIntent())
            return false; // don't support expo bracketing mode if called from image capture intent
        return preview.supportsExpoBracketing();
    }

    public boolean supportsFocusBracketing() {
        if (applicationInterface.isImageCaptureIntent())
            return false; // don't support focus bracketing mode if called from image capture intent
        return preview.supportsFocusBracketing();
    }

    public boolean supportsFastBurst() {
        if (applicationInterface.isImageCaptureIntent())
            return false; // don't support burst mode if called from image capture intent
        // require 512MB just to be safe, due to the large number of images that may be created
        return (preview.usingCamera2API() && large_heap_memory >= 512 && preview.supportsBurst());
    }

    public boolean supportsNoiseReduction() {
        // require at least Android 5, for the Renderscript support in HDRProcessor, but we require
        // Android 7 to limit to more modern devices (for performance reasons)
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && preview.usingCamera2API() && large_heap_memory >= 512 && preview.supportsBurst() && preview.supportsExposureTime());
        //return false; // currently blocked for release
    }


    public boolean supportsForceVideo4K() {
        return this.supports_force_video_4k;
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    private void disableForceVideo4K() {
        this.supports_force_video_4k = false;
    }




    public Preview getPreview() {
        return this.preview;
    }

    public PermissionHandler getPermissionHandler() {
        return permissionHandler;
    }


    public MainUI getMainUI() {
        return this.mainUI;
    }






    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        permissionHandler.onRequestPermissionsResult(requestCode, grantResults);
    }



}
