package com.example.textrecognition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.impl.utils.Exif;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {


    private static  final int REQUEST_IMAGE_CAPTURE=672;
    private String imageFilePath;
    private Uri photoUri;

    private Bitmap imageBitmap;
    private ImageView iv_result;
    private TextView recognition_text;
    private Button mTextbutton,btn_capture;
    private LanguageIdentifier languageIdentification;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        recognition_text=(TextView)findViewById(R.id.recognition_text);
        iv_result=(ImageView) findViewById(R.id.iv_result);
        mTextbutton=(Button) findViewById(R.id.mTextButton);
        btn_capture=(Button) findViewById(R.id.btn_capture);

        languageIdentification = LanguageIdentification.getClient();
        // Any new instances of LanguageIdentification needs to be closed appropriately.
        // LanguageIdentification automatically calls close() on the ON_DESTROY lifecycle event,
        // so here we can add our languageIdentification instance as a LifecycleObserver for this
        // activity and have it be closed when this activity is destroyed.
        getLifecycle().addObserver(languageIdentification);


        //권한 체크
        TedPermission.with(getApplicationContext())
                .setPermissionListener(permissionListener)
                .setRationaleMessage("카메라 권한이 필요합니다.")
                .setDeniedMessage("거부하겠습니다.")
                .setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.CAMERA)
                .check();

        findViewById(R.id.btn_capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if(intent.resolveActivity(getPackageManager()) !=null){
                    File photoFile=null;
                    try{
                        photoFile=createImageFile();
                    }catch(IOException e){

                    }
                    if(photoFile!=null){
                        photoUri= FileProvider.getUriForFile(getApplicationContext(),
                                getPackageName(),
                                photoFile);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
                        startActivityForResult(intent,REQUEST_IMAGE_CAPTURE);
                    }
                }
            }
        });
        findViewById(R.id.mTextButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTextRecognition();
            }
        });
    }


    private File createImageFile() throws IOException {
        String timeStamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName="TEST_"+timeStamp+"_";
        File storageDir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image=File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        imageFilePath=image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
            ExifInterface exif = null;
            //Bundle extras=data.getExtras();
            //imageBitmap=(Bitmap) extras.get("data");
            imageBitmap=bitmap;

            //iv_result.setImageBitmap(imageBitmap);
            try {
                exif = new ExifInterface(imageFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            int exifOrientation;
            int exifDegree;

            if (exif != null) {
                exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                exifDegree = exifOrientationToDegree(exifOrientation);
            } else {
                exifDegree = 0;
            }
            ((ImageView) findViewById(R.id.iv_result)).setImageBitmap(rotate(bitmap, exifDegree));
        }
    }

    private void runTextRecognition() {
        InputImage image = InputImage.fromBitmap(imageBitmap,0);
        TextRecognizer recognizer = TextRecognition.getClient();
        findViewById(R.id.mTextButton).setEnabled(false);
        recognizer.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text texts) {
                                findViewById(R.id.mTextButton).setEnabled(true);
                                processTextRecognitionResult(texts);
                                identifyLanguage(texts.getText());
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                findViewById(R.id.mTextButton).setEnabled(true);
                                e.printStackTrace();
                            }
                        });
    }

    private void processTextRecognitionResult(Text texts) {
        List<Text.TextBlock> blocks = texts.getTextBlocks();
        if (blocks.size() == 0) {
            Toast.makeText(this, "No text found", Toast.LENGTH_SHORT).show();
            return;
        } else {
            for(Text.TextBlock block: texts.getTextBlocks())
            {
                String text=block.getText();
                recognition_text.setText(text);
            }
        }
    }

    private int exifOrientationToDegree(int exifOrientation){
        if(exifOrientation==ExifInterface.ORIENTATION_ROTATE_90){
            return 90;
        }else if(exifOrientation==ExifInterface.ORIENTATION_ROTATE_180){
            return 180;
        }else if(exifOrientation==ExifInterface.ORIENTATION_ROTATE_270){
            return 270;
        }else
            return 0;
    }


    private Bitmap rotate(Bitmap bitmap,float degree){
        Matrix matrix=new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
    }



    PermissionListener permissionListener=new PermissionListener() {
        @Override
        public void onPermissionGranted() {
            Toast.makeText(getApplicationContext(),"권한이 허용됨.",Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissions) {
            Toast.makeText(getApplicationContext(),"권한이 거부됨.",Toast.LENGTH_SHORT).show();

        }
    };
    private void identifyPossibleLanguages(final String inputText) {
        languageIdentification = LanguageIdentification.getClient();
        languageIdentification
                .identifyPossibleLanguages(inputText)
                .addOnSuccessListener(
                        this,
                        new OnSuccessListener<List<IdentifiedLanguage>>() {
                            @Override
                            public void onSuccess(List<IdentifiedLanguage> identifiedLanguages) {
                                List<String> detectedLanguages =
                                        new ArrayList<>(identifiedLanguages.size());
                                for (IdentifiedLanguage language : identifiedLanguages) {
                                    detectedLanguages.add(
                                            String.format(
                                                    Locale.US,
                                                    "%s (%3f)",
                                                    language.getLanguageTag(),
                                                    language.getConfidence())
                                    );
                                }
                                recognition_text.append(
                                        String.format(
                                                Locale.US,
                                                "\n%s - [%s]",
                                                inputText,
                                                TextUtils.join(", ", detectedLanguages)));
                            }
                        })
                .addOnFailureListener(
                        this,
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Language identification error", e);
                                Toast.makeText(
                                        MainActivity.this, R.string.language_id_error,
                                        Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
    }

    private void identifyLanguage(final String inputText) {
        languageIdentification
                .identifyLanguage(inputText)
                .addOnSuccessListener(
                        this,
                        new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(String s) {
                                recognition_text.append(
                                        String.format(
                                                Locale.US,
                                                "\n%s - %s",
                                                inputText,
                                                s));
                            }
                        })
                .addOnFailureListener(
                        this,
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Language identification error", e);
                                Toast.makeText(
                                        MainActivity.this, R.string.language_id_error,
                                        Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
    }

    private void setConfidence() {
        // [START set_confidence]
        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(
                new LanguageIdentificationOptions.Builder()
                        .setConfidenceThreshold(0.34f)
                        .build());
        // [END set_confidence]
    }

    private void getPossibleLanguages(String text) {
        // [START get_possible_languages]
        LanguageIdentifier languageIdentifier =
                LanguageIdentification.getClient();
        languageIdentifier.identifyPossibleLanguages(text)
                .addOnSuccessListener(new OnSuccessListener<List<IdentifiedLanguage>>() {
                    @Override
                    public void onSuccess(List<IdentifiedLanguage> identifiedLanguages) {
                        for (IdentifiedLanguage identifiedLanguage : identifiedLanguages) {
                            String language = identifiedLanguage.getLanguageTag();
                            float confidence = identifiedLanguage.getConfidence();
                            Log.i(TAG, language + " (" + confidence + ")");
                        }
                    }
                })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Model couldn’t be loaded or other internal error.
                                // ...
                            }
                        });
        // [END get_possible_languages]
    }

    private void setConfidenceThreshold() {
        // [START set_confidence_threshold]
        LanguageIdentificationOptions identifierOptions =
                new LanguageIdentificationOptions.Builder()
                        .setConfidenceThreshold(0.5f)
                        .build();
        LanguageIdentifier languageIdentifier = LanguageIdentification
                .getClient(identifierOptions);
        // [END set_confidence_threshold]
    }
}
