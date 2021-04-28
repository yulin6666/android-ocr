/*
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.sfsu.cs.orange.ocr;

import java.io.File;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.THRESH_OTSU;

/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message,
 * and dismiss the indeterminate progress dialog box. Used for non-continuous mode OCR only.
 */
final class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, Boolean> {

  //  private static final boolean PERFORM_FISHER_THRESHOLDING = false; 
  //  private static final boolean PERFORM_OTSU_THRESHOLDING = false; 
  //  private static final boolean PERFORM_SOBEL_THRESHOLDING = false; 

  static{ System.loadLibrary("opencv_java3"); }

  private CaptureActivity activity;
  private TessBaseAPI baseApi;
  private byte[] data;
  private int width;
  private int height;
  private OcrResult ocrResult;
  private long timeRequired;

  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, byte[] data, int width, int height) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.data = data;
    this.width = width;
    this.height = height;
  }

  @Override
  protected Boolean doInBackground(Void... arg0) {
    long start = System.currentTimeMillis();
    Bitmap bitmap = activity.getCameraManager().buildLuminanceSource(data, width, height).renderCroppedGreyscaleBitmap();

//    //尝试用OpenCV变换
//    Mat src = new Mat(bitmap.getHeight(),bitmap.getWidth(), CvType.CV_8UC4);
//    Utils.bitmapToMat(bitmap, src);
//    Imgcodecs.imwrite("/sdcard/src.png",src);
//
//    Mat gray = new Mat();
//    Imgproc.cvtColor(src,gray,COLOR_BGR2GRAY);
//
//    Mat sobel = new Mat();
//    Imgproc.Sobel(gray,sobel,0,1,0,3);
//
//    Mat threshold = new Mat();
//    Imgproc.threshold(sobel,threshold,0,255,THRESH_BINARY+THRESH_OTSU);
//
//    Mat element1 = Imgproc.getStructuringElement(MORPH_RECT,new Size(30,9));
//    Mat element2 = Imgproc.getStructuringElement(MORPH_RECT,new Size(24,6));
//    Mat dialation = new Mat();
//    Imgproc.dilate(threshold,dialation,element2,new Point(-1,-1),1);
//
//    Mat erosion = new Mat();
//    Imgproc.erode(dialation,erosion,element1,new Point(-1,-1),1);
//
//    Mat dilation2 = new Mat();
//    Imgproc.dilate(erosion,dilation2,element2,new Point(-1,-1),3);
//    Imgcodecs.imwrite("/sdcard/dilation2.png",dilation2);

    String textResult;
    String needResult="";


    //      if (PERFORM_FISHER_THRESHOLDING) {
    //        Pix thresholdedImage = Thresholder.fisherAdaptiveThreshold(ReadFile.readBitmap(bitmap), 48, 48, 0.1F, 2.5F);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }
    //      if (PERFORM_OTSU_THRESHOLDING) {
    //        Pix thresholdedImage = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bitmap), 48, 48, 9, 9, 0.1F);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }
    //      if (PERFORM_SOBEL_THRESHOLDING) {
    //        Pix thresholdedImage = Thresholder.sobelEdgeThreshold(ReadFile.readBitmap(bitmap), 64);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }

    try {     
      baseApi.setImage(ReadFile.readBitmap(bitmap));
      textResult = baseApi.getUTF8Text();
      timeRequired = System.currentTimeMillis() - start;

      // Check for failure to recognize text
      if (textResult == null || textResult.equals("")) {
        return false;
      }
      ocrResult = new OcrResult();
      ocrResult.setWordConfidences(baseApi.wordConfidences());
      ocrResult.setMeanConfidence( baseApi.meanConfidence());
      ocrResult.setRegionBoundingBoxes(baseApi.getRegions().getBoxRects());
      ocrResult.setTextlineBoundingBoxes(baseApi.getTextlines().getBoxRects());
      ocrResult.setWordBoundingBoxes(baseApi.getWords().getBoxRects());
      ocrResult.setStripBoundingBoxes(baseApi.getStrips().getBoxRects());


      Log.e("yulin","wordConfidences size:"+baseApi.wordConfidences().length+",meanConfidence:"+baseApi.meanConfidence());

      // Iterate through the results.
      final ResultIterator iterator = baseApi.getResultIterator();
      int[] lastBoundingBox;
      ArrayList<Rect> charBoxes = new ArrayList<Rect>();
      iterator.begin();
      int i = 0;
      String[] words = new String[20];
      boolean find = false;
      do {
          lastBoundingBox = iterator.getBoundingBox(PageIteratorLevel.RIL_WORD);
          String word = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD);
          words[i]=word;

          Log.e("yulin","word:"+word);

          if(word.equals("度")&& !find){//找到第一个度
            Log.e("yulin","找到了第一个度");
              int j = i -1;
              Log.e("yulin","前一个置信度:"+baseApi.wordConfidences()[j]+",值:"+words[j]);
              int k = j -1;
              Log.e("yulin","前前一个置信度:"+baseApi.wordConfidences()[k]+",值:"+words[k]);
              if(baseApi.wordConfidences()[j] > 50 && baseApi.wordConfidences()[k] > 50){
                needResult = words[k];
                needResult += ".";
                needResult += words[j];
              }else{
                return false;
              }
              find = true;
          }
          i++;

          Rect lastRectBox = new Rect(lastBoundingBox[0], lastBoundingBox[1],
                  lastBoundingBox[2], lastBoundingBox[3]);
          charBoxes.add(lastRectBox);
      } while (iterator.next(PageIteratorLevel.RIL_WORD));

      if(!find){
        return false;
      }
      iterator.delete();
      ocrResult.setCharacterBoundingBoxes(charBoxes);

    } catch (RuntimeException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
      e.printStackTrace();
      try {
        baseApi.clear();
        activity.stopHandler();
      } catch (NullPointerException e1) {
        // Continue
      }
      return false;
    }
    timeRequired = System.currentTimeMillis() - start;
    ocrResult.setBitmap(bitmap);
    ocrResult.setText(needResult);
    ocrResult.setRecognitionTimeRequired(timeRequired);
    return true;
  }

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    Handler handler = activity.getHandler();
    if (handler != null) {
      // Send results for single-shot mode recognition.
      if (result) {
        Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
        message.sendToTarget();
      } else {
        Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      activity.getProgressDialog().dismiss();
    }
    if (baseApi != null) {
      baseApi.clear();
    }
  }
}
