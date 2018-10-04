package net.thesysadmin.nebba;

import java.lang.ref.WeakReference;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * This Class is the the core of NEBBA.
 * In this class are all possible manipulations defined.
 * The manipulations are done in the background through asyncTask.
 * At the beginning of a calculation the Progress-Dialog is shown to the user.
 * After the calculation the bitmap is set to the ImageView in the UI and
 * to the fields "currentImage" and "resultImage" in StartActivity-Class
 * In Case of an error (i.e. OutOfMemoryError) the Alert-Dialog is displayed.
 * @author Martin Mueller, 8881847
 *
 */
public class ImageManipulator extends AsyncTask<Object, Integer, Bitmap> {

	private static final String APPNAME = "NEBBA";
	private final WeakReference<ImageView> imageViewReference;
	
	private static int currentProgress = 0;
	private static int maxProgress = 0;
	private static int tenthMaxProgress = 0;

	ProgressDialog mProgressDialog = null;
	Context myContext;
	Boolean taskSuccessful=true;
	int selectedTool;
	ImageView activityImageView;

	public ImageManipulator(Context context, ImageView imageView) {
		myContext = context;
		activityImageView = imageView;
		imageViewReference = new WeakReference<ImageView>(imageView);
	}

	@Override
	protected void onPreExecute() {
		mProgressDialog = new ProgressDialog(myContext);
		mProgressDialog.setTitle(myContext.getResources().getString(R.string.please_wait));
		mProgressDialog.setMessage(myContext.getResources().getString(R.string.applying_changes));       
		mProgressDialog.setIndeterminate(false);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(true);
		mProgressDialog.setCanceledOnTouchOutside(false);
		mProgressDialog.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				Toast.makeText(myContext, R.string.canceled, Toast.LENGTH_SHORT).show();
				
			}
		});
		mProgressDialog.show();
	}

	protected void onProgressUpdate(Integer... values) {
		if (values.length == 2) {
			mProgressDialog.setProgress(values[0]);
			mProgressDialog.setMax(values[1]);
		}
	}

	@Override
	protected Bitmap doInBackground(Object... params) {

		Log.d (APPNAME, "ASyncTask ImageManipulator started");
		selectedTool = (Integer) params[0];
		int seekBarValue = (Integer) params[1];
		Bitmap bitmap = (Bitmap)params[2];
		Bitmap newBitmap = bitmap;
		int direction=0;
		int startX=0;
		int startY=0;
		int stopX=0;
		int stopY=0;
		Boolean removeSeams=false;
		if (selectedTool==9) {
			// for seam-carving more parameter are needed
			direction= (Integer) params[3];
			removeSeams = (Boolean) params[4];
			startX = (Integer) params[5];
			startY = (Integer) params[6];
			stopX = (Integer) params[7];
			stopY = (Integer) params[8];
		}

		try {

//			Log.d(APPNAME, "Manipulating Image");
			if (selectedTool==1) {
				newBitmap = changeSaturation(bitmap, seekBarValue);
			}
			else if (selectedTool==2) {
				newBitmap = changeColor(bitmap, seekBarValue);
			}
			else if (selectedTool==3) {
				newBitmap = changeHue(bitmap, seekBarValue);
			}
			else if (selectedTool==4) {
				newBitmap = changeBrightness(bitmap, seekBarValue);
			}
			else if (selectedTool==5) {
				newBitmap = changeContrast(bitmap, seekBarValue);
			}
			else if (selectedTool==6) {
				newBitmap = normalizedHistogram(bitmap);
			}
			else if (selectedTool==7) {
				newBitmap = blurImage(bitmap);
			}
			else if (selectedTool==8) {
				newBitmap = sharpenImage(bitmap);			
			}
			else if (selectedTool==9) {
				newBitmap = seamCarving(bitmap, seekBarValue, direction, removeSeams, startX, startY, stopX, stopY);
			}
			else {
				newBitmap = bitmap;
			}

		}
		catch (OutOfMemoryError oome) {
//			Log.d(APPNAME, "OOME occoured");
			newBitmap = bitmap;
			taskSuccessful = false;
		}
		StartActivity.setTaskWasSuccessful(taskSuccessful);
		return newBitmap;
	}
	

	@Override
	protected void onPostExecute(Bitmap result) {
		if (imageViewReference != null && result != null) {
			final ImageView imageView = imageViewReference.get();
			if (selectedTool>=6) {
				// the image is be set directly to the object "currentImage" and "resultImage"
				// the user can not change any parameters, otherwise he can use the undo button
				StartActivity.setCurrentImage(result);
				StartActivity.setResultImage(result);
			}
			else {
				// the result is now in resultImage, maybe the user changes some parameters
				// then the object currentImage will be used for the manipulation
				StartActivity.setResultImage(result);
			}
			imageView.setImageBitmap(result);
		}
		mProgressDialog.dismiss();
		mProgressDialog = null;
		maxProgress=0;
		if (!taskSuccessful) {
			showOOEMDialog();
		}
//		Log.d (APPNAME, "ASyncTask finished");
	}
	
	private void showOOEMDialog() {
		new AlertDialog.Builder(myContext)
	    .setTitle(myContext.getResources().getString(R.string.error))
	    .setMessage(myContext.getResources().getString(R.string.error_oome))
	    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 

	        }
	     })
	    .setIcon(android.R.drawable.ic_dialog_alert)
	    .show();
	}

	/**
	 * This method changes the saturation of a given bitmap
	 * @param bitmap  --> the original bitmap
	 * @param value   --> the value of the seekbar (0-510)
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap changeSaturation(Bitmap bitmap, int value) {
//		Log.d(APPNAME, "ImageManipulator: changeSaturation started");

		Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

		float choosenSaturation = (float) value / 256;
//		Log.d(APPNAME, "Saturation: " + String.valueOf(choosenSaturation));

		Canvas canvasResult = new Canvas(newBitmap);
		ColorMatrix colorMatrix = new ColorMatrix();
		colorMatrix.setSaturation(choosenSaturation);
		ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
		Paint paintObject = new Paint();
		paintObject.setColorFilter(filter);
		canvasResult.drawBitmap(bitmap, 0, 0, paintObject);
		bitmap = null;

//		Log.d(APPNAME, "ImageManipulator: changeSaturation finished");
		return newBitmap;
	}

	/**
	 * This method changes the hue-value of a given bitmap
	 * @param bitmap  --> the original bitmap
	 * @param value   --> the value of the seekbar (0-510)
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap changeHue(Bitmap bitmap, int value) {
//		Log.d(APPNAME, "ImageManipulator: changeHue started");

		Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

		float choosenHue;
		if (value == 0) {
			choosenHue=1;
		}
		else {
			choosenHue = (float) value / 255 * 180;
		}
//		Log.d(APPNAME, "Hue is now: " + String.valueOf(choosenHue));

		float cosinus = (float) Math.cos(choosenHue);
		float sinus = (float) Math.sin(choosenHue);
		float red = 0.213f;
		float green = 0.715f;
		float blue = 0.072f;
		float[] colorTransformMatrix = new float[] { 
				red + cosinus * (1 - red) + sinus * (-red), green + cosinus * (-green) + sinus * (-green), blue + cosinus * (-blue) + sinus * (1 - blue),0,0, 
				red + cosinus * (-red) + sinus * (0.143f), green + cosinus * (1 - green) + sinus * (0.140f), blue + cosinus * (-blue) + sinus * (-0.283f),0,0,
				red + cosinus * (-red) + sinus * (-(1 - red)), green + cosinus * (-green) + sinus * (green), blue + cosinus * (1 - blue) + sinus * (blue),0,0, 
				0,0,0,1,0, 
				0,0,0,0,1, 
		};

		ColorMatrix colorMatrix = new ColorMatrix();
		colorMatrix.set(colorTransformMatrix);
		ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
		Canvas canvasResult = new Canvas(newBitmap);
		Paint paintObject = new Paint();
		paintObject.setColorFilter(colorFilter);
		canvasResult.drawBitmap(bitmap, 0, 0, paintObject);
		bitmap = null;

//		Log.d(APPNAME, "ImageManipulator: changeHue finished");
		return newBitmap;
	}

	/**
	 * This method changes the color of a given bitmap (to red, green, blue)
	 * @param bitmap  --> the original bitmap
	 * @param value   --> the value of the seekbar (0-510)
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap changeColor(Bitmap bitmap, int value) {
//		Log.d(APPNAME, "ImageManipulator: changeColor started");

		Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

		int choosenColor = value;
		int red = 1;
		int green = 1;
		int blue = 1;

		if (choosenColor<=170) {
			green=0;
			blue=0;
		}
		else if (choosenColor>170 && choosenColor<=340) {
			red=0;
			blue=0;
		}
		else if (choosenColor>341) {
			red=0;
			green=0;
		}

		float[] colorTransformMatrix = {
				red,0,0,0,0,
				0,green,0,0,0,
				0,0,blue,0,0,
				0,0,0,1,0,
				0,0,0,0,1,
		};

		ColorMatrix colorMatrix = new ColorMatrix();
		colorMatrix.set(colorTransformMatrix);
		ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
		Canvas canvasResult = new Canvas(newBitmap);
		Paint paintObject = new Paint();
		paintObject.setColorFilter(colorFilter);
		canvasResult.drawBitmap(bitmap, 0, 0, paintObject);
		bitmap = null;

//		Log.d(APPNAME, "ImageManipulator: changeColor finished");
		return newBitmap;
	}

	/**
	 * This method changes the brightness of a given bitmap
	 * @param bitmap  --> the original bitmap
	 * @param value   --> the value of the seekbar (0-510)
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap changeBrightness(Bitmap bitmap, int value) {
//		Log.d(APPNAME, "ImageManipulator: changeBrightnesss started");

		Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

		// normalize values to range from -100 to 100 and guarantee the image is not black or white
		int choosenBrightness = value-255;
		if (choosenBrightness>100) {
			choosenBrightness = 100;
		}
		if (choosenBrightness<-100) {
			choosenBrightness = -100;
		}

		float[] colorTransformMatrix = {
				1,0,0,0,choosenBrightness,
				0,1,0,0,choosenBrightness,
				0,0,1,0,choosenBrightness,
				0,0,0,1,0,
				0,0,0,0,1,
		};

		ColorMatrix colorMatrix = new ColorMatrix();
		colorMatrix.set(colorTransformMatrix);
		ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
		Canvas canvasResult = new Canvas(newBitmap);
		Paint paintObject = new Paint();
		paintObject.setColorFilter(colorFilter);
		canvasResult.drawBitmap(bitmap, 0, 0, paintObject);
		bitmap = null;

//		Log.d(APPNAME, "ImageManipulator: changeBrightness finished");
		return newBitmap;
	}

	/**
	 * This method changes the contrast of a given bitmap
	 * @param bitmap  --> the original bitmap
	 * @param value   --> the value of the seekbar (0-510)
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap changeContrast(Bitmap bitmap, int value) {
//		Log.d(APPNAME, "ImageManipulator: changeContrast started");

		// value must be between 0 and 1.. 
		float choosenContrast = ((value-255)/255.f)+1.f;
		float averageBrightness = (-.5f * choosenContrast + .5f) * 255.f;

		Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

		float[] colorTransformMatrix = {
				choosenContrast,0,0,0,averageBrightness,
				0,choosenContrast,0,0,averageBrightness,
				0,0,choosenContrast,0,averageBrightness,
				0,0,0,1,0,
				0,0,0,0,1,
		};

		ColorMatrix colorMatrix = new ColorMatrix();
		colorMatrix.set(colorTransformMatrix);
		ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
		Canvas canvasResult = new Canvas(newBitmap);
		Paint paintObject = new Paint();
		paintObject.setColorFilter(colorFilter);
		canvasResult.drawBitmap(bitmap, 0, 0, paintObject);
		bitmap = null;

//		Log.d(APPNAME, "ImageManipulator: changeContrast finished");
		return newBitmap;
	}

	/**
	 * This method blur the given bitmap
	 * It is also used by the method "sharpenImage" 
	 * @param bitmap  --> the original bitmap
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap blurImage(Bitmap bitmap) {
//		Log.d(APPNAME, "ImageManipulator: blurImage started");
		
		int bitmapHeight = bitmap.getHeight();
		int bitmapWidth = bitmap.getWidth();

		// if this function is called from another function, maxProgress is != 0
//		if (maxProgress==0) {
			maxProgress=bitmapHeight*bitmapWidth;
			tenthMaxProgress = maxProgress/10;
//		}
		
		int alpha;
		int red;
		int green;
		int blue;
		int sumRed;
		int sumGreen;
		int sumBlue;

		double divisor=16;
		double offset=0;

		int[][]imageMatrix = new int[3][3];
		int[][]gaussMatrix = {
				{1,2,1},
				{2,4,2},
				{1,2,1},
		};
//		Log.d(APPNAME, "ImageManipulator: Gauss-Matrix created and values initialized");
		
		Bitmap newBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);

		for (int y=0; y<bitmapHeight-(imageMatrix.length-1); y++) {
			for (int x=0; x<bitmapWidth-(imageMatrix.length-1); x++) {
				for (int i=0; i<imageMatrix.length; i++) {
					for (int j=0; j<imageMatrix.length; j++){
						imageMatrix[i][j] = bitmap.getPixel(x+i, y+j);
					}
				}

				alpha = Color.alpha(imageMatrix[1][1]);
				sumRed=0;
				sumGreen=0;
				sumBlue=0;

				for (int i=0; i<imageMatrix.length; i++) {
					for (int j=0; j<imageMatrix.length; j++){
						sumRed = sumRed + (Color.red(imageMatrix[i][j]) * gaussMatrix[i][j]);
						sumGreen = sumGreen + (Color.green(imageMatrix[i][j]) * gaussMatrix[i][j]);
						sumBlue = sumBlue + (Color.blue(imageMatrix[i][j]) * gaussMatrix[i][j]);
					}
				}

				red = (int)(sumRed/divisor+offset);
				green = (int)(sumGreen/divisor+offset);
				blue = (int)(sumBlue/divisor+offset);

				// normalize values
				if (red<0) {
					red=0;
				}
				else if (red>255) {
					red=255;
				}
				if (green<0) {
					green=0;
				}
				else if (green>255) {
					green=255;
				}
				if (blue<0) {
					blue=0;
				}
				else if (blue>255) {
					blue=255;
				}
				newBitmap.setPixel(x+1, y+1, Color.argb(alpha, red, green, blue));
				currentProgress = currentProgress+1;
				if (currentProgress%tenthMaxProgress==0){
					publishProgress(currentProgress, maxProgress);
				}
			}
		}
		
//		Log.d(APPNAME, "ImageManipulator: blurImage finished");
		return newBitmap;
	}

	/**
	 * This method sharpen the given bitmap
	 * First, it calls the method "blurImage" to get a blurred image
	 * @param bitmap  --> the original bitmap
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap sharpenImage(Bitmap bitmap) {
//		Log.d(APPNAME, "ImageManipulator: sharpenImage started");
		
		int bitmapHeight = bitmap.getHeight();
		int bitmapWidth = bitmap.getWidth();

		// if this function is called from another function, maxProgress is != 0
//		if (maxProgress==0) {
			maxProgress=bitmapHeight*bitmapWidth*2;
			tenthMaxProgress = maxProgress/10;
//		}

		Bitmap blurredBitmap = blurImage(bitmap);
		int [][] imageArray = new int[bitmapHeight][bitmapWidth];
		int [][] blurredImageArray = new int[bitmapHeight][bitmapWidth];
		
		for (int x=0; x<bitmapWidth; x++) {
			for (int y=0; y<bitmapHeight; y++) {
				// get the bitmap pixels
				imageArray[y][x] = bitmap.getPixel(x,y);
				blurredImageArray[y][x] = blurredBitmap.getPixel(x,y);
			}
		}
		blurredBitmap.recycle();
		blurredBitmap = null;
		bitmap = null;

		Bitmap newBitmap = Bitmap.createBitmap(imageArray[0].length, imageArray.length, Bitmap.Config.ARGB_8888);
		for (int x=0; x<imageArray[0].length; x++) {
			for (int y=0; y<imageArray.length; y++) {
				int alpha=(imageArray[y][x] >> 24) + ((imageArray[y][x] >> 24) - (blurredImageArray[y][x] >> 24));
				int red=((imageArray[y][x] >> 16) & 0xFF) + ((imageArray[y][x] >> 16) & 0xFF)-((blurredImageArray[y][x] >> 16) & 0xFF);
				int green=((imageArray[y][x] >> 8) & 0xFF) + ((imageArray[y][x] >> 8) & 0xFF)-((blurredImageArray[y][x] >> 8) & 0xFF);
				int blue=((imageArray[y][x] >> 0) & 0xFF)+((imageArray[y][x] >> 0) & 0xFF)-((blurredImageArray[y][x] >> 0) & 0xFF);
				// normalize values
				if (red<0) {
					red=0;
				}
				else if (red>255) {
					red=255;
				}
				if (green<0) {
					green=0;
				}
				else if (green>255) {
					green=255;
				}
				if (blue<0) {
					blue=0;
				}
				else if (blue>255) {
					blue=255;
				}
				newBitmap.setPixel(x,y,Color.argb(alpha, red, green, blue));
				currentProgress = currentProgress+1;
				if (currentProgress%tenthMaxProgress==0){
					publishProgress(currentProgress, maxProgress);
				}
			}
		}

//		Log.d(APPNAME, "ImageManipulator: sharpenImage finished");
		return newBitmap;
	}

	/**
	 * This method calculates the equalized histogram of the bitmap
	 * and apply it to the bitmap
	 * @param bitmap  --> the original bitmap
	 * @return bitmap --> the new bitmap
	 */
	public Bitmap normalizedHistogram(Bitmap bitmap) {
//		Log.d(APPNAME, "ImageManipulator: normalizedHistogram started");
		
		int bitmapWidth = bitmap.getWidth();
		int bitmapHeight = bitmap.getHeight();
		int numberPixels = bitmapHeight*bitmapWidth;
		int[] histogramData=new int[256];
		float[] cumulizedData=new float[256];
		float[] normalizedData=new float[256];
		int [][] imageArray = new int[bitmapHeight][bitmapWidth];

		// if this function is called from another function, maxProgress is != 0
//		if (maxProgress==0) {
			maxProgress=numberPixels*2+histogramData.length+cumulizedData.length;
			tenthMaxProgress = maxProgress/10;
//		}

		for (int i=0; i<histogramData.length; i++) {
			histogramData[i]=0;
			cumulizedData[i]=0;
			normalizedData[i]=0;
		}

		// generate histogram with V-channel
		for (int x=0; x<bitmapWidth; x++) {
			for (int y=0; y<bitmapHeight; y++) {
				int red = Color.red(bitmap.getPixel(x,y));
				int green = Color.green(bitmap.getPixel(x,y));
				int blue = Color.blue(bitmap.getPixel(x,y));

				float[] hsv = new float[3];
				hsv = rgb2hsv(red/255f, green/255f, blue/255f);
				int v = (int) (255*hsv[2]);
				histogramData[v] = histogramData[v]+1;
				imageArray[y][x] = bitmap.getPixel(x,y);
				
				currentProgress = currentProgress+1;
				if (currentProgress%tenthMaxProgress==0){
					publishProgress(currentProgress, maxProgress);
				}
			}
		}
		bitmap = null;

		for (int i=0; i<histogramData.length; i++) {
			normalizedData[i] = (float) histogramData[i]/ (float) numberPixels;
			
			currentProgress = currentProgress+1;
			if (currentProgress%tenthMaxProgress==0){
				publishProgress(currentProgress, maxProgress);
			}
		}

		cumulizedData[0] = 0;
		for (int i=1; i<cumulizedData.length; i++) {
			cumulizedData[i] = cumulizedData[i-1]+normalizedData[i];
			
			currentProgress = currentProgress+1;
			if (currentProgress%tenthMaxProgress==0){
				publishProgress(currentProgress, maxProgress);
			}
		}

//		Log.d(APPNAME, "Finished calculating histogram");

        Bitmap newBitmap = Bitmap.createBitmap(imageArray[0].length, imageArray.length, Bitmap.Config.ARGB_8888);
        for (int x=0; x<imageArray[0].length; x++) {
        	for (int y=0; y<imageArray.length; y++) {
        		int alpha=(imageArray[y][x] >> 24);
        		int red=(imageArray[y][x] >> 16) & 0xFF;
        		int green=(imageArray[y][x] >> 8) & 0xFF;
        		int blue=(imageArray[y][x] >> 0) & 0xFF;
        		float[] hsv = new float[3];
				int[] rgb = new int[3];

				hsv = rgb2hsv(red/255f, green/255f, blue/255f);

				int v = (int) (255*hsv[2]);
				float newV = cumulizedData[v]; 

				rgb = hsv2rgb(hsv[0],hsv[1],newV);
				newBitmap.setPixel(x, y, Color.argb(alpha, rgb[0], rgb[1], rgb[2]));
				
				currentProgress = currentProgress+1;
				if (currentProgress%tenthMaxProgress==0){
					publishProgress(currentProgress, maxProgress);
				}
        	}
        }

//		Log.d(APPNAME, "ImageManipulator: normalizedHistogram finished");
		return newBitmap;
	}

	/**
	 * This method implements the seamCarving algorithm to the bitmap
	 * It has two options to apply the algorithm: 
	 * - classic (removeSeams not checked) -> remove the given number of seams (seamRuns)
	 * - removeArea (removeSeams checked) -> remove the area given with startX/Y | stopX/Y
	 * @param bitmap         --> the original bitmap
	 * @param seamRuns       --> how many seams to be removed
	 * @param seamDirection  --> int 1 (vertical) or 2 (horizontal)
	 * @param removeSeams    --> boolean, if true, the selected seams will be removed
	 * @param startX         --> start x-axis of the rectangle to remove
	 * @param startY         --> start y-axis of the rectangle to remove
	 * @param stopX          --> stop x-axis of the rectangle to remove
	 * @param stopY          --> stop y-axis of the rectangle to remove
	 * @return bitmap        --> the new bitmap
	 */
	public Bitmap seamCarving(Bitmap bitmap, int seamRuns, int seamDirection, boolean removeSeams, int startX, int startY, int stopX, int stopY) {
//		Log.d(APPNAME, "ImageManipulator: seamCarving started");
	
		seamRuns = (seamRuns-510)*-1;
		Bitmap newBitmap = null;
		int bitmapWidth = bitmap.getWidth();
		int bitmapHeight = bitmap.getHeight();
	
		int [][] imageArray = new int[bitmapHeight][bitmapWidth];
		int [][] seamRemoveArray = new int[bitmapHeight][bitmapWidth];
		float [][] bitmapArray = new float[bitmapHeight][bitmapWidth];
		float [][] energyArray;
		float [][] seamArray;
	
		int absStartX=0;
		int absStartY=0;
		int absStopX=0;
		int absStopY=0;
	
		if (removeSeams) {
			if (startX==0 && stopX==0 && startY==0 && stopY==0) {
				seamRuns=0;
			}
			else {
				if (startX>stopX) {
					int tmp = startX;
					startX = stopX;
					stopX = tmp;
				}
				if (startY>stopY) {
					int tmp = startY;
					startY = stopY;
					stopY = tmp;
				}
				absStartX = startX*bitmapWidth/activityImageView.getWidth();
				absStartY = startY*bitmapHeight/activityImageView.getHeight();
				absStopX = stopX*bitmapWidth/activityImageView.getWidth();
				absStopY = stopY*bitmapHeight/activityImageView.getHeight();
//				Log.d(APPNAME, "absStartX = "+absStartX+" | absStopX = "+absStopX+" | absStartY = "+absStartY+" | absStopY = "+absStopY);
				if (seamDirection==1) {
					seamRuns = (absStopX-absStartX);
				}
				else if (seamDirection==2) {
					seamRuns = (absStopY-absStartY);
				}
			}
		}
		
//		if (maxProgress==0) {
			currentProgress = 0;
			maxProgress=seamRuns+100;
			tenthMaxProgress = maxProgress/10;
//		}
		
		currentProgress = currentProgress+10;
		publishProgress(currentProgress, maxProgress);
	
		// convert bitmap to  BitmapArray and ImageArray
		for (int x=0; x<bitmapWidth; x++) {
			for (int y=0; y<bitmapHeight; y++) {
				// get the bitmap pixels
				int red = Color.red(bitmap.getPixel(x,y));
				int green = Color.green(bitmap.getPixel(x,y));
				int blue = Color.blue(bitmap.getPixel(x,y));
	
				// convert to hsv
				float[] hsv = new float[3];
				hsv = rgb2hsv(red/255f, green/255f, blue/255f);
	
				// fill data into array's
				bitmapArray[y][x] = hsv[2];
				imageArray[y][x] = bitmap.getPixel(x,y);
			}
		}
		currentProgress = currentProgress+40;
		publishProgress(currentProgress, maxProgress);
		
		// delete reference for GC
		bitmap = null;
	
		if (absStartX<0) {
			absStartX=0;
		}
		if (absStartY<0) {
			absStartY=0;
		}
		if (absStopX>=seamRemoveArray[0].length) {
			absStopX=seamRemoveArray[0].length-1;
		}
		if (absStopY>=seamRemoveArray.length) {
			absStopY=seamRemoveArray.length-1;
		}
		// create removeSeamsArray if necessary
		if (removeSeams) {
			for (int x=absStartX; x<absStopX; x++) {
				for (int y=absStartY; y<absStopY; y++) {
					seamRemoveArray[y][x] = 1;
				}
			}
		}
	
		// start looping
		for (int a=1; a<=seamRuns; a++) {  
	
//			Log.d(APPNAME, "remove seam: "+a);
			// create EnergyArray
			energyArray = new float[bitmapArray.length][bitmapArray[0].length];
			for (int x=0; x<bitmapArray.length; x++) {
				for (int y=0; y<bitmapArray[x].length; y++) {
					if (x==0 || x==bitmapArray.length-1) {
						if (y==0 || y==bitmapArray[x].length-1) {
							energyArray[x][y] = bitmapArray[x][y];
						}
						else {
							energyArray[x][y] = Math.abs(bitmapArray[x][y+1]-bitmapArray[x][y-1]);
						}
					}
					else if (y==0 || y==bitmapArray[x].length-1) {
						energyArray[x][y] = Math.abs(bitmapArray[x+1][y]-bitmapArray[x-1][y]);
					}
					else {
						energyArray[x][y] = (Math.abs(bitmapArray[x+1][y]-bitmapArray[x-1][y])+Math.abs(bitmapArray[x][y+1]-bitmapArray[x][y-1]));
					}
				}
			}
	
			// manipulate energy-array, if removeSeams is set to true
			if (removeSeams) {
				for (int x=0; x<energyArray.length;x++) {
					for (int y=0; y<energyArray[x].length; y++) {
						if (seamRemoveArray[x][y]==1) {
							energyArray[x][y]=-10;
						}
					}
				}
			}
	
			// create seams-Array
			seamArray = new float[energyArray.length][energyArray[0].length];
			for (int x=0; x<seamArray.length; x++) {
				for (int y=0; y<seamArray[x].length-1; y++) {
					if (x==0) {
						seamArray[x][y] = energyArray[x][y];
					}
					else {
						if (y==0) {
							// first entry, has no left member
							seamArray[x][y] = energyArray[x][y] + (Math.min(seamArray[x-1][y],seamArray[x-1][y+1]));
						}
						else if (y==energyArray[x].length) {
							// last entry, has no right member
							seamArray[x][y] = energyArray[x][y] + (Math.min(seamArray[x-1][y-1],seamArray[x-1][y]));
						}
        				else {
        					if (energyArray[x][y+1]<0) {
        						seamArray[x][y] = energyArray[x][y] + (Math.min(seamArray[x-1][y-1],seamArray[x-1][y]));
        					}
        					else if (energyArray[x][y-1]<0) {
        						seamArray[x][y] = energyArray[x][y] + (Math.min(seamArray[x-1][y],seamArray[x-1][y+1]));
        					}
        					else {
        						seamArray[x][y] = energyArray[x][y] + (Math.min((Math.min(seamArray[x-1][y-1],seamArray[x-1][y])),seamArray[x-1][y+1]));
        					}
        				}
					}
				}
			}
	
			float [][] newBitmapArray = null;
			int [][] newImageArray = null;
	
			if (seamDirection==1) {  // vertical
				// find seam
				int[] verticalSeam = new int[seamArray.length];
				float[] tempArray = new float[seamArray[0].length];
	
				// copy last line into temp-array 
				for (int x=0; x<tempArray.length; x++) {
					tempArray[x] = seamArray[seamArray.length-1][x];
				}
				// find minimum and it's position in (temp)-array
				float min = 99.0f;
				int minPosition = 0;
				for (int i=0; i<tempArray.length-1; i++) {
					if (tempArray[i] < min) {
						min = tempArray[i];
						minPosition = i;
					}
				}
	
				verticalSeam[seamArray.length-1] = minPosition;
	
				// now create the rest of the seam-path
				for (int x=seamArray.length-2; x>=0; x--) {
					// copy current line into temp-array 
					for (int i=0; i<tempArray.length-1; i++) {
						tempArray[i] = seamArray[x][i];
					}
					min = tempArray[minPosition];
					int first;
					int last;
					if (minPosition == 0) {
						first=minPosition;
						last=minPosition+1;
					}
					else if (minPosition == tempArray.length-1) {
						first=minPosition-1;
						last=minPosition;
					}
					else {
						first=minPosition-1;
						last=minPosition+1;
					}
					for (int i=first; i<=last; i++) {
						if (tempArray[i] < min) {
							min = tempArray[i];
							minPosition = i;
						}
					}
					verticalSeam[x] = minPosition;
				}
	
				// remove the pixel from Bitmap and Image-Array
				newBitmapArray = new float[bitmapArray.length][bitmapArray[0].length-1];
				newImageArray = new int[imageArray.length][imageArray[0].length-1];
				int toRemovePixel;
				for (int x=0; x<bitmapArray.length; x++) {
					int offset = 0;
					for (int y=0; y<bitmapArray[x].length-1; y++) {
						toRemovePixel = verticalSeam[x];
						if (y==toRemovePixel) {
							offset++;
							seamRemoveArray[x][y] = 0;
						}
						newBitmapArray[x][y] = bitmapArray[x][y+offset];
						newImageArray[x][y] = imageArray[x][y+offset];
					}
				}
			} // if
	
			else if (seamDirection==2) { // horizontal
				// find seam
				int[] horizontalSeam = new int[seamArray[0].length];
				float[] tempArray = new float[seamArray.length];
	
				// copy first row into temp-array 
				for (int x=0; x<tempArray.length; x++) {
					tempArray[x] = seamArray[x][0];
				}
				// find minimum and it's position in (temp)-array
				float min = 99.0f;
				int minPosition = 0;
				for (int i=0; i<tempArray.length-1; i++) {
					if (tempArray[i] < min) {
						min = tempArray[i];
						minPosition = i;
					}
				}
	
				horizontalSeam[0] = minPosition;
	
				// now create the rest of the seam-path
				for (int x=1; x<seamArray[0].length; x++) {
					// copy current line into temp-array 
					for (int i=0; i<tempArray.length; i++) {
						tempArray[i] = seamArray[i][x];
					}
					min = tempArray[minPosition];
					int first;
					int last;
					if (minPosition == 0) {
						first=minPosition;
						last=minPosition+1;
					}
					else if (minPosition == tempArray.length-1) {
						first=minPosition-1;
						last=minPosition;
					}
					else {
						first=minPosition-1;
						last=minPosition+1;
					}
					for (int i=first; i<=last; i++) {
						if (tempArray[i] < min) {
							min = tempArray[i];
							minPosition = i;
						}
					}
					horizontalSeam[x] = minPosition;
				}
	
				// remove the pixel from Bitmap and Image-Array
				newBitmapArray = new float[bitmapArray.length-1][bitmapArray[0].length];
				newImageArray = new int[imageArray.length-1][imageArray[0].length];
	
				int toRemovePixel;
				for (int y=0; y<bitmapArray[0].length; y++) {
					toRemovePixel=horizontalSeam[y];
					int offset=0;
					for (int x=0; x<bitmapArray.length-1; x++) {
						if (x==toRemovePixel) {
							offset++;
							seamRemoveArray[x][y] = 0;
						}
						newBitmapArray[x][y] = bitmapArray[x+offset][y];
						newImageArray[x][y] = imageArray[x+offset][y];
					}
				}
			}  // else if
	
			bitmapArray = newBitmapArray;
			imageArray = newImageArray;
			
			currentProgress = currentProgress+1;
			if (currentProgress%tenthMaxProgress==0){
				publishProgress(currentProgress, maxProgress);
			}
			
		} // for
	
		// create the result-Image
//		Log.d(APPNAME, "Converting Image back to Bitmap");
	
		newBitmap = Bitmap.createBitmap(imageArray[0].length, imageArray.length, Bitmap.Config.ARGB_8888);
		for (int x=0; x<imageArray[0].length; x++) {
			for (int y=0; y<imageArray.length; y++) {
				int alpha=(imageArray[y][x] >> 24);
				int red=(imageArray[y][x] >> 16) & 0xFF;
				int green=(imageArray[y][x] >> 8) & 0xFF;
				int blue=(imageArray[y][x] >> 0) & 0xFF;
				newBitmap.setPixel(x,y,Color.argb(alpha, red, green, blue));
				
			}
		}
		currentProgress = currentProgress+50;
		publishProgress(currentProgress, maxProgress);
	
//		Log.d(APPNAME, "ImageManipulator: seamCarving finished");
		return newBitmap;
	}

	/**
	 * converts rgb-values to hsv
	 * @param red   --> the red value (0-255)
	 * @param green --> the green value (0-255)
	 * @param blue  --> the blue value (0-255)
	 * @return hsv[3] --> the hsv-values in a float array
	 */
	private float[] rgb2hsv(float red, float green, float blue) {

		float min;
		float max;
		float[] hsv = new float[3];

		if (red > green) { 
			min = green; 
			max = red; 
		}
		else { 
			min = red; 
			max = green; 
		}
		if (blue > max) {
			max = blue;
		}
		if (blue < min) {
			min = blue;
		}

		float H = 0;
		float S = 0;
		float V = max;

		if ( max != 0) { 
			S = (max-min)/max; 
		}
		else {
			S = 0;
			H = 0;
		}

		if (max == red) 
			H = (60* (0+ (green-blue)/(max-min)));
		else if (max == green) 
			H = (60* (2 +(blue-red)/(max-min)));
		else if (max == blue) 
			H = (60* (4 +(red-green)/(max-min)));   

		if (H<0) {
			H = H+360;
		}

		hsv[0] = H;
		hsv[1] = S;
		hsv[2] = V;

		return hsv;
	}

	/**
	 * converts hsv-values to rgb
	 * @param hue   --> the hue (0-360)
	 * @param saturation --> the saturation (0-1)
	 * @param value  --> the value (0-1)
	 * @return rgb[3] --> the rgb-values in a integer array
	 */
	private int[] hsv2rgb(float hue, float saturation, float value) {

		int[] rgb = new int[3];
		if (saturation==0){
			rgb[0] = rgb[1] = rgb[2] = (int) (value * 255.0f + 0.5f);
		}
		else {
			float h = hue/60;
			float f = h - (float) Math.floor(h);
			float p = (value * (1.0f-saturation));
			float q = (value * (1.0f-saturation*f));
			float t = (value * (1.0f-saturation*(1.0f-f)));

			int hInt = (int) h;
			if (hInt == 0) {
				rgb[0] = (int) (255*value);
				rgb[1] = (int) (255*t);
				rgb[2] = (int) (255*p);
			}
			else if (hInt==1) {
				rgb[0] = (int) (255*q);
				rgb[1] = (int) (255*value);
				rgb[2] = (int) (255*p);
			}
			else if (hInt==2) {
				rgb[0] = (int) (255*p);
				rgb[1] = (int) (255*value);
				rgb[2] = (int) (255*t);
			}
			else if (hInt==3) {
				rgb[0] = (int) (255*p);
				rgb[1] = (int) (255*q);
				rgb[2] = (int) (255*value);
			}
			else if (hInt==4) {
				rgb[0] = (int) (255*t);
				rgb[1] = (int) (255*p);
				rgb[2] = (int) (255*value);
			}
			else if (hInt==5) {
				rgb[0] = (int) (255*value);
				rgb[1] = (int) (255*p);
				rgb[2] = (int) (255*q);
			}
		}

		return rgb;
	}

}
