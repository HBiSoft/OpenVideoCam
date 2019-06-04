package com.HBiSoft.OpenVideoCam;

import android.Manifest;
import android.content.Intent;


import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.HBiSoft.OpenVideoCam.OpenCamera.camView;

import java.io.File;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    Button camNoAudio, camNoAnimation, camH264, camH263, camSetBitrate;
    int REQUEST_CODE_MY_PICK = 100;
    TextView delete;
    Uri fileUri;

    Button openCam;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //openCam = findViewById(R.id.openCam);

        camNoAudio = findViewById(R.id.camNoAudio);
        /*camNoAnimation = findViewById(R.id.camNoAnimation);
        camH264 = findViewById(R.id.camH264);
        camH263 = findViewById(R.id.camH263);
        camSetBitrate = findViewById(R.id.camSetBitrate);*/

        delete = findViewById(R.id.delete);
        openCam =findViewById(R.id.openCam);



        String sessionId= getIntent().getStringExtra("success");
        if (sessionId != null && sessionId.equals("success")){
            delete.setText("Successfully Saved");
        }

        /*openCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("new");

            }
        });*/

        camNoAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("old", "camNoAudio");
            }
        });

        openCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("new", "camNoAudio");

            }
        });

        /*camNoAnimation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("old", "camNoAnimation");
            }
        });
        camH264.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("old", "camH264");
            }
        });
        camH263.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("old", "camH263");
            }
        });
        camSetBitrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera("old", "camSetBitrate");
            }
        });*/

    }

    @AfterPermissionGranted(123)
    private void openCamera(String oldOrNew, String extra) {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};
        if (EasyPermissions.hasPermissions(MainActivity.this, perms)) {
            if (oldOrNew.equals("new")) {
                final Intent nintent = new Intent();
                nintent.setClass(MainActivity.this, camView.class);
                startActivity(nintent);
                finish();
            }

        } else {
            EasyPermissions.requestPermissions(MainActivity.this, "The app needs permissions to record video's on this device",
                    123, perms);
        }
    }



    public void sendEmail(File file){
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"contact.hbisoft@com"});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Error Log");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Error log attached");
        //File root = Environment.getExternalStorageDirectory();
        //String pathToMyAttachedFile = "temp/attachement.xml";
        //File file = new File(root, pathToMyAttachedFile);
        if (!file.exists() || !file.canRead()) {
            return;
        }
        //Uri uri = Uri.fromFile(file);
        Uri uri = FileProvider.getUriForFile(getApplication(), getApplication().getPackageName() + ".provider", file);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        //startActivity(Intent.createChooser(emailIntent, "Pick an Email provider"));
        startActivityForResult(emailIntent, REQUEST_CODE_MY_PICK);
        //finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_MY_PICK) {
            //String appName = data.getComponent().flattenToShortString();
            // Now you know the app being picked.
            // data is a copy of your launchIntent with this important extra info added.

            // Don't forget to start it!
            delete.setText("You can delete the app now");
            Toast.makeText(this, "Thank you John", Toast.LENGTH_LONG).show();
        }

        if (requestCode == 123){

            Toast.makeText(this, String.valueOf(fileUri), Toast.LENGTH_SHORT).show();

        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        String sessionId= getIntent().getStringExtra("EXTRA_SESSION_ID");
        if (sessionId != null && sessionId.equals("sendLog")){
            //File root = new File(Environment.getExternalStorageDirectory(), "Notes/LogError.txt");
            File root = getBaseContext().getExternalFilesDir("Temp/LogError.txt");
            getIntent().removeExtra("EXTRA_SESSION_ID");
            sendEmail(root);
        }
    }


}
