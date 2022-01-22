
package org.tensorflow.lite.examples.superresolution;

// Gives access to the Content of asset folder
import android.content.res.AssetFileDescriptor;

// Access and management of asset folder.
import android.content.res.AssetManager;

// Bitmap Library Stores Color of Pixel bits in Matrix format
import android.graphics.Bitmap;

//Creates Bitmap objects from various sources, including files, streams, and byte-arrays.
import android.graphics.BitmapFactory;

// A Drawable that wraps a bitmap and can be tiled, stretched, or aligned.
import android.graphics.drawable.BitmapDrawable;

//Uri
import android.net.Uri;

//Android Bundle is used to pass data between activities.
import android.os.Bundle;

// Gives support for Realtime Hardware Clock
import android.os.SystemClock;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

//Object used to report movement (mouse, pen, finger, trackball) events
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.WorkerThread;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** A super resolution class to generate super resolution images from low resolution images * */
public class MainActivity extends AppCompatActivity {
  // Loading the Main Library
  static {
    System.loadLibrary("SuperResolution");
  }
  // To get Input Images from Storage
  ActivityResultLauncher<String>mgetcontent;

  // Defining Functions
  private static final String TAG = "SuperResolution";
  private static final String MODEL_NAME = "ESRGAN.tflite";
  private static final int LR_IMAGE_HEIGHT = 50;
  private static final int LR_IMAGE_WIDTH = 50;
  private static final int UPSCALE_FACTOR = 4;
  private static final int SR_IMAGE_HEIGHT = LR_IMAGE_HEIGHT * UPSCALE_FACTOR;
  private static final int SR_IMAGE_WIDTH = LR_IMAGE_WIDTH * UPSCALE_FACTOR;
  private static final String LR_IMG_1 = "lr-1.jpg";

  Button btn;

  private MappedByteBuffer model;
  private long superResolutionNativeHandle = 0;
  private Bitmap selectedLRBitmap = null;
  private boolean useGPU = false;
  private ImageView lowResImageView1;
  private Switch gpuSwitch;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // Opening intent to get Images from Storage
    mgetcontent=registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
      @Override
      public void onActivityResult(Uri result) {
        lowResImageView1.setImageURI(result);

      }
    });

    final Button superResolutionButton = findViewById(R.id.upsample_button);
    lowResImageView1 =findViewById(R.id.low_resolution_image_1);
     btn = findViewById(R.id.b1);
    gpuSwitch = findViewById(R.id.switch_use_gpu);

    //Uploading Image from Storage
    btn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mgetcontent.launch("image/*");
      }
    });


    //Defining Array of Image
    ImageView[] lowResImageViews = {lowResImageView1};

    AssetManager assetManager = getAssets();
    // Checking Images from Asset folder
    try {
      InputStream inputStream1 = assetManager.open(LR_IMG_1);
      Bitmap bitmap1 = BitmapFactory.decodeStream(inputStream1);
      lowResImageView1.setImageBitmap(bitmap1);

    } catch (IOException e) {
      Log.e(TAG, "Failed to open an low resolution image");
    }

    for (ImageView iv : lowResImageViews) {
      setLRImageViewListener(iv);
    }
    // Checking Whether we have chosen Image or Not
    superResolutionButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            if (selectedLRBitmap == null) {
              Toast.makeText(
                      getApplicationContext(),
                      "Please choose one low resolution image",
                      Toast.LENGTH_LONG)
                  .show();
              return;
            }

            if (superResolutionNativeHandle == 0) {
                superResolutionNativeHandle = initTFLiteInterpreter(gpuSwitch.isChecked());
            } else if (useGPU != gpuSwitch.isChecked()) {
              // We need to reinitialize interpreter when execution hardware is changed
              deinit();
              superResolutionNativeHandle = initTFLiteInterpreter(gpuSwitch.isChecked());
            }
            useGPU = gpuSwitch.isChecked();
            if (superResolutionNativeHandle == 0) {
              showToast("TFLite interpreter failed to create!");
              return;
            }

            // Getting Pixel Count of Image
            int[] lowResRGB = new int[LR_IMAGE_HEIGHT * LR_IMAGE_WIDTH];
            selectedLRBitmap.getPixels(
                lowResRGB, 0, LR_IMAGE_WIDTH, 0, 0, LR_IMAGE_WIDTH, LR_IMAGE_HEIGHT);

            //Gives Execution Time in ms
            final long startTime = SystemClock.uptimeMillis();
            int[] superResRGB = doSuperResolution(lowResRGB);
            final long processingTimeMs = SystemClock.uptimeMillis() - startTime;
            if (superResRGB == null) {
              showToast("Super resolution failed!");
              return;
            }

            final LinearLayout resultLayout = findViewById(R.id.result_layout);
            final ImageView superResolutionImageView = findViewById(R.id.super_resolution_image);
            final ImageView nativelyScaledImageView = findViewById(R.id.natively_scaled_image);
            final TextView superResolutionTextView = findViewById(R.id.super_resolution_tv);
            final TextView nativelyScaledImageTextView =
                findViewById(R.id.natively_scaled_image_tv);
            final TextView logTextView = findViewById(R.id.log_view);

            // Force refreshing the ImageView
            superResolutionImageView.setImageDrawable(null);
            Bitmap srImgBitmap =
                Bitmap.createBitmap(
                    superResRGB, SR_IMAGE_WIDTH, SR_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            superResolutionImageView.setImageBitmap(srImgBitmap);
            nativelyScaledImageView.setImageBitmap(selectedLRBitmap);
            resultLayout.setVisibility(View.VISIBLE);
            logTextView.setText("Inference time: " + processingTimeMs + "ms");
          }
        });
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    deinit();
  }
  //Shows the Name of Selected image
  // NOTE: Not Necessary
  private void setLRImageViewListener(ImageView iv) {
    iv.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            if (v.equals(lowResImageView1)) {
              selectedLRBitmap = ((BitmapDrawable) lowResImageView1.getDrawable()).getBitmap();

            }
            return false;
          }
        });
  }

  @WorkerThread
  public synchronized int[] doSuperResolution(int[] lowResRGB) {
    return superResolutionFromJNI(superResolutionNativeHandle, lowResRGB);
  }


  private MappedByteBuffer loadModelFile() throws IOException {
    try (AssetFileDescriptor fileDescriptor =
            AssetsUtil.getAssetFileDescriptorOrCached(getApplicationContext(), MODEL_NAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
      FileChannel fileChannel = inputStream.getChannel();
      long startOffset = fileDescriptor.getStartOffset();
      long declaredLength = fileDescriptor.getDeclaredLength();
      return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
  }

  private void showToast(String str) {
    Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG).show();
  }

  private long initTFLiteInterpreter(boolean useGPU) {
    try {
      model = loadModelFile();
    } catch (IOException e) {
      Log.e(TAG, "Fail to load model", e);
    }
    return initWithByteBufferFromJNI(model, useGPU);
  }

  private void deinit() {
    deinitFromJNI(superResolutionNativeHandle);
  }

  private native int[] superResolutionFromJNI(long superResolutionNativeHandle, int[] lowResRGB);

  private native long initWithByteBufferFromJNI(MappedByteBuffer modelBuffer, boolean useGPU);

  private native void deinitFromJNI(long superResolutionNativeHandle);
}
