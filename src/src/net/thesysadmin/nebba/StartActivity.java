package net.thesysadmin.nebba; 

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;


/**
 * This class hold the UI and most of the logic of NEBBA.
 * It is also the Launcher-Activity of the app.
 * @author Martin Mueller, 8881847
 *
 */
public class StartActivity extends Activity implements OnItemSelectedListener, OnSeekBarChangeListener {
	
	static final String APPNAME = "NEBBA";
	static final int REQUEST_IMAGE_CAPTURE = 10;
	static final int REQUEST_IMAGE_SELECT = 20;
	static final int SEEKBARINITIALVALUE = 255;
	static final int SEEKBARMAXVALUE = 510;
	static final String imageUndoFileName = "NebbaUndoFile.jpg";
	static final String imageRedoFileName = "NebbaRedoFile.jpg";
	
	// UI Controls
	ImageView mImageView;
	TextView mTextViewExtraInfo;
	Spinner mSpinner;
	SeekBar mSeekBar;
	ProgressBar mProgressBar;
	LinearLayout controls;
	FrameLayout imageFrameLayout;
	ImageView undoButton;
	ImageView redoButton;
	ImageView acceptButton;
	ZoomControls zoomButtons;
	CheckBox mCheckBox;
	RadioButton mRadioButtonHorizontal;
	RadioButton mRadioButtonVertical;
	
	float imageStartX;
	float imageStartY;

	Uri imageUri; 
	int selectedTool;
	int seekBarValue;
	int seekBarUndoValue;
	int seekBarRedoValue;
	static Bitmap currentImage;
	static Bitmap resultImage;
	String imageCachePath;
	String imageSavePath;
	String imageFileName;
	File imageCacheDirectory;
	boolean externalStorageAvailable;

	int maxX;
	int maxY;
	float rectStartX, rectStartY, rectStopX, rectStopY;
	int startX, startY, stopX, stopY;

	static Boolean taskWasSuccessful = true;
	private static LruCache<String, Bitmap> mMemoryCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d (APPNAME, "onCreate started");
		
		// set fixed orientation for phones
		if (!checkIfTablet()) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		setContentView(R.layout.activity_start);
		
		if (currentImage == null) {
			Toast.makeText(this, R.string.select_picture_first, Toast.LENGTH_SHORT).show();
		}
		initializeUI();
	
		// create LruCache for faster loading
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		final int cacheSize = maxMemory / 2;
		
		mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

	}

	/**
	 * This method initializes the elements used for the UI
	 */
	private void initializeUI() {
	
		// create containers
		imageFrameLayout = (FrameLayout) findViewById(R.id.FrameLayoutPicture);
		mImageView = (ImageView) findViewById(R.id.ImageViewPicture);
	
		controls = (LinearLayout) findViewById(R.id.LayoutControls);
		controls.setVisibility(View.INVISIBLE);
		
		mTextViewExtraInfo = (TextView) findViewById(R.id.TextViewExtraInfo);
		
		// create buttons
		zoomButtons = (ZoomControls) findViewById(R.id.mZoomControls);
		undoButton = (ImageView) findViewById(R.id.ButtonUndoButton);
		undoButton.setVisibility(View.INVISIBLE);
		redoButton = (ImageView) findViewById(R.id.ButtonRedoButton);
		redoButton.setVisibility(View.INVISIBLE);
		acceptButton = (ImageView) findViewById(R.id.ButtonAcceptButton);
		acceptButton.setVisibility(View.INVISIBLE);
		
		// create Spinner Object 
		mSpinner = (Spinner) findViewById(R.id.SpinnerTool);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.available_tools, R.layout.spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinner.setAdapter(adapter);
		mSpinner.setOnItemSelectedListener(this);
		
		// create SeekBar Object
		mSeekBar = (SeekBar) findViewById(R.id.SeekBarToolValue);
		mSeekBar.setOnSeekBarChangeListener(this);
		mSeekBar.setVisibility(View.INVISIBLE);
		
		// create controls for seamcarving
		mRadioButtonVertical = (RadioButton) findViewById(R.id.OptionVertical);
		mRadioButtonHorizontal = (RadioButton) findViewById(R.id.OptionHorizontal);
		mCheckBox = (CheckBox) findViewById(R.id.checkBox);
		mRadioButtonVertical.setVisibility(View.INVISIBLE);
		mRadioButtonHorizontal.setVisibility(View.INVISIBLE);
		mCheckBox.setVisibility(View.INVISIBLE);
	
	}

	/**
	 * This method sets the Listeners to elements of the UI
	 * i.e. zoom-in and zoom-out listener and touch events
	 */
	private void setListeners() {
			
			// after orientation-change imageFrameLayout is 0, so we saved maxX & maxY on restore :/
			if (maxX == 0 & maxY == 0) {
				maxX = (int)(imageFrameLayout.getWidth()/2);
				maxY = (int)(imageFrameLayout.getHeight()/2);
			}
		
			// set scroll limits
			final int maxLeft = (maxX * -1);
			final int maxRight = (maxX);
			final int maxTop = (maxY * -1);
			final int maxBottom = (maxY);
			mImageView.setOnTouchListener(new View.OnTouchListener() {
				
				float downX, downY;
				int totalX, totalY;
				int scrollByX, scrollByY;
	
				@Override
				public boolean onTouch(View view, MotionEvent event)
				{
					if (selectedTool!=9) {
						// default behavior, move the image in the imageView around
						float currentX, currentY;
						switch (event.getAction())
						{
						case MotionEvent.ACTION_DOWN:
							downX = event.getX();
							downY = event.getY();
							break;
						case MotionEvent.ACTION_MOVE:
							currentX = event.getX();
							currentY = event.getY();
							scrollByX = (int)(downX - currentX);
							scrollByY = (int)(downY - currentY);
							if (currentX > downX) {
								if (totalX == maxLeft)  {
									scrollByX = 0;
								}
								if (totalX > maxLeft) {
									totalX = totalX + scrollByX;
								}
								if (totalX < maxLeft) {
									scrollByX = maxLeft-(totalX-scrollByX);
									totalX = maxLeft;
								}
							}
	
							if (currentX < downX) {
								if (totalX == maxRight) {
									scrollByX = 0;
								}
								if (totalX < maxRight) {
									totalX = totalX + scrollByX;
								}
								if (totalX > maxRight) {
									scrollByX = maxRight - (totalX - scrollByX);
									totalX = maxRight;
								}
							}
	
							if (currentY > downY) {
								if (totalY == maxTop) {
									scrollByY = 0;
								}
								if (totalY > maxTop) {
									totalY = totalY + scrollByY;
								}
								if (totalY < maxTop) {
									scrollByY = maxTop - (totalY - scrollByY);
									totalY = maxTop;
								}
							}
	
							if (currentY < downY) {
								if (totalY == maxBottom) {
									scrollByY = 0;
								}
								if (totalY < maxBottom) {
									totalY = totalY + scrollByY;
								}
								if (totalY > maxBottom) {
									scrollByY = maxBottom - (totalY - scrollByY);
									totalY = maxBottom;
								}
							}
							view.scrollBy(scrollByX, scrollByY);
							downX = currentX;
							downY = currentY;
							break;
						}
	
					}
					else {
						// selected tool is 9 (seam-carving), so we have to paint with the values
						// of motionEvent a rectangle on the bitmap 
						switch (event.getAction())
						{
						case MotionEvent.ACTION_DOWN:
							startX = (int) event.getX();
							rectStartX = event.getX();
							startY = (int) event.getY();
							rectStartY = event.getY();
	//						Log.d(APPNAME, "startX = "+startX+" | startY = "+startY);
							break;
						case MotionEvent.ACTION_UP:
							stopX = (int) event.getX();
							rectStopX = event.getX();
							stopY = (int) event.getY();
							rectStopY = event.getY();
	//						Log.d(APPNAME, "stopX = "+stopX+" | stopY = "+stopY);
							mTextViewExtraInfo.setText("Start XY = "+startX+","+startY+"  Stop XY = "+stopX+","+stopY+"");
							paintRectangleOnBitmap();
							break;
						}
					}
					return true;
				}
			});
			
			zoomButtons.setOnZoomInClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					float x = mImageView.getScaleX();
					float y = mImageView.getScaleY();
					mImageView.setScaleX((float) (x+1));
					mImageView.setScaleY((float) (y+1));
				}
			});
			zoomButtons.setOnZoomOutClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					float x = mImageView.getScaleX();
					float y = mImageView.getScaleY();
					if ((x>imageStartX) || (y>imageStartY)) {
						mImageView.setScaleX((float) (x-1));
						mImageView.setScaleY((float) (y-1));
					}
				}
			});
			
		}

	/** 
	 * This method create all paths and filenames used by the app
	 */
	private void createPathsAndFilenames() {
		
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
		imageFileName = "IMG_"+timeStamp+".jpg";
		
		File imageDir=null;
		File cacheDir=null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			imageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), APPNAME);
			if (!imageDir.exists()){
				imageDir.mkdirs();
			}
			imageSavePath = imageDir.toString();
			
			cacheDir = new File(getExternalFilesDir(null), APPNAME + "-Cache");
			if (!cacheDir.exists()){
				cacheDir.mkdirs();
			}
			imageCachePath = cacheDir.toString();
			imageUri = Uri.fromFile(new File(cacheDir.getPath() + File.separator + imageFileName));
		}
		else {
			Toast.makeText(this, R.string.external_storage_not_found, Toast.LENGTH_SHORT).show();
			imageCachePath = getCacheDir().toString();
			imageUri = Uri.fromFile(new File(getCacheDir().getPath() + File.separator + imageFileName));
		}
	
	}

	/**
	 * This method creates a copy of the image saved in "currentImage",
	 * paints a rectangle on it and show it in the ImageView.
	 */
	private void paintRectangleOnBitmap() {
	//    	Log.d(APPNAME, "paintRectangleOnBitmap() started");
			try {
				
	    	Bitmap paintBitmap = currentImage.copy(currentImage.getConfig(), true);
	    	Canvas canvas = new Canvas(paintBitmap);
	        Paint paint = new Paint();
	        paint.setColor(Color.RED);
	        paint.setStyle(Paint.Style.STROKE);
	        paint.setStrokeWidth(5);
	        canvas.drawRect((rectStartX*currentImage.getWidth()/mImageView.getWidth()), (rectStartY*currentImage.getHeight()/mImageView.getHeight()), (rectStopX*currentImage.getWidth()/mImageView.getWidth()), (rectStopY*currentImage.getHeight()/mImageView.getHeight()), paint);
	        mImageView.setImageBitmap(paintBitmap);
			}
	        
			catch (OutOfMemoryError oome) {
//				Log.d(APPNAME, "OOME occurred");
				mImageView.setImageBitmap(currentImage);
				taskWasSuccessful = false;
				showOOEMDialog();
			}
	    }
	
	private void showOOEMDialog() {
		new AlertDialog.Builder(this)
	    .setTitle(getResources().getString(R.string.error))
	    .setMessage(getResources().getString(R.string.error_oome))
	    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 

	        }
	     })
	    .setIcon(android.R.drawable.ic_dialog_alert)
	    .show();
	}

	/**
	 * This method adds a new entry to the LruCache for Images
	 * @param key    --> a identifier (as string)
	 * @param bitmap --> the bitmap to put into the cache
	 */
	public static void addBitmapToMemoryCache(String key, Bitmap bitmap) {
//		Log.d(APPNAME, "Bitmap added to cache, Key: "+key);
		mMemoryCache.put(key, bitmap);
	}
	
	/**
	 * This method returns a bitmap from the LruCache for Images
	 * @param key     --> a identifier (as string)
	 * @return bitmap --> the bitmap saved at key-position
	 */
	public static Bitmap getBitmapFromMemCache(String key) {
//		Log.d(APPNAME, "Bitmap loaded from cache, Key: "+key);
	    return mMemoryCache.get(key);
	}
	
	/**
	 * This method tries to load an image from the LruCache.
	 * If it doesn't find the bitmap in cache, it will decode it from given params
	 * @param path     --> the path to the file in file system
	 * @param filename --> the filename and key in the LruCache
	 */
	public void loadImageAndSetToScreen(String path, String filename) {
//		Log.d(APPNAME, "loadImageAndSetToScreen: Start for file " + filename);
		Bitmap bitmap=null;
		
		// filename is null, when restoring configuration and no image is selected before
		if (filename != null) {
			bitmap = getBitmapFromMemCache(filename);
		}
		
		// first check cache, if the image is there, otherwise decode it
	    if (bitmap != null) {
//	    	Log.d(APPNAME, "load Bitmap from cache");
	        mImageView.setImageBitmap(bitmap);
	        currentImage = bitmap;
	    } 
	    else {
//	    	Log.d(APPNAME, "Bitmap not in cache, must be decoded");
	    	File mImageFile = new File(path + File.separator + filename);
	    	try {
	    		BitmapDecodeTask bdt = new BitmapDecodeTask(this, mImageView);
	    		bdt.execute(new FileInputStream(mImageFile), filename);
	    	}
	    	catch (FileNotFoundException e) {
	    		Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
//	    		Log.d(APPNAME, "Error: File not found" + e.getMessage());
	    	}
	    }
	}
	
	/**
	 * Saves the bitmap into a file
	 * @param bitmap     --> the bitmap to save
	 * @param path       --> the path, where the bitmap should be saved
	 * @param filename   --> the filename for the file
	 * @param persistent --> if true, a message will be displayed after successful saving 
	 */
	public void saveImage(Bitmap bitmap, String path, String filename, Boolean persistent) {
		BitmapSaveTask bst = new BitmapSaveTask(this);
		bst.execute(bitmap, path, filename, persistent);
	}
	
	/**
	 * Checks if a camera is existing on the system
	 * @return true if a camera exists, false if not
	 */
	public boolean checkForCamera() {
		int numCameras = Camera.getNumberOfCameras();
		if (numCameras > 0) {
			return true;
		}
		else {
			return false;
		}
	}
	/**
	 * Checks if the current system is a tablet
	 * @return true if it is a tablet, false if not
	 */
	public boolean checkIfTablet() {
	    return (getResources().getConfiguration().screenLayout
	            & Configuration.SCREENLAYOUT_SIZE_MASK)
	            >= Configuration.SCREENLAYOUT_SIZE_LARGE;
	}

	/**
	 * Restores saved program-data used by the app, after the app was interrupted
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
//		Log.d(APPNAME, "onRestoreInstanceState started");
		seekBarValue = savedInstanceState.getInt("seekbar");
		selectedTool = savedInstanceState.getInt("spinner");
		imageCachePath = savedInstanceState.getString("imageCachePath");
		imageSavePath = savedInstanceState.getString("imageSavePath");
		imageFileName = savedInstanceState.getString("filename");
		loadImageAndSetToScreen(imageCachePath, imageFileName);
		if (currentImage != null) {
			mImageView.setImageBitmap(currentImage);
			imageStartX = mImageView.getScaleX();
			imageStartY = mImageView.getScaleY();
			maxX = savedInstanceState.getInt("maxX");
			maxY = savedInstanceState.getInt("maxY");
			setListeners();
//			Log.d(APPNAME, "loading values: seekbarValue: "+seekBarValue+" selectedTool: "+selectedTool+"");
			controls.setVisibility(View.VISIBLE);
			if (selectedTool != 0 & seekBarValue != SEEKBARINITIALVALUE) {
				undoButton.setVisibility(View.VISIBLE);
				acceptButton.setVisibility(View.VISIBLE);
			}
			mSpinner.setSelection(selectedTool);
		}
		else {
			Toast.makeText(this, R.string.select_picture_first, Toast.LENGTH_SHORT).show();
		}
		super.onRestoreInstanceState(savedInstanceState);
	
	}

	/**
	 * Saves the program-data currently used by the app, when it is interrupted
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
//		Log.d(APPNAME, "onSaveInstanceState started");
//		Log.d(APPNAME, "saving values: seekbarValue: "+seekBarValue+" selectedTool: "+selectedTool+"");
		if (currentImage != null) {
			saveImage(currentImage, imageCachePath, imageFileName, false);
			savedInstanceState.putString("imageCachePath", imageCachePath);
			savedInstanceState.putString("imageSavePath", imageSavePath);
			savedInstanceState.putString("filename", imageFileName);
			savedInstanceState.putInt("seekbar", seekBarValue);
			savedInstanceState.putInt("spinner", selectedTool);
			savedInstanceState.putParcelable("imageUri", imageUri);
			savedInstanceState.putInt("maxX", maxX);
			savedInstanceState.putInt("maxY", maxY);
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Creates the menu for the app
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.start, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * Depending on the selection by the user, the requested task
	 * will be executed (close, save, take picture, select from gallery)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) { 
		switch (item.getItemId()) {
		case R.id.action_about:
//			Log.d (APPNAME, "Menu-Option About selected");
			startActivity(new Intent(this,AboutActivity.class));
			return true;
		case R.id.action_close:
//			Log.d (APPNAME, "Menu-Option Exit selected");
			finish();
			return true;
		case R.id.action_save:
//			Log.d (APPNAME, "Menu-Option Save selected");
			if (currentImage != null) {
				// save the current displayed image
				setCurrentImage(resultImage);
				saveImage(currentImage, imageSavePath, imageFileName, true);		
			}
			else {
				Toast.makeText(this, R.string.error_saving_picture, Toast.LENGTH_SHORT).show();
			}
			return true;
		case R.id.action_get_picture_from_gallery:
//			Log.d (APPNAME, "Menu-Option Gallery selected");
			createPathsAndFilenames();
			if (currentImage!=null) {
				currentImage=null;
			}
			Intent galleryIntent = new Intent();
			galleryIntent.setType("image/*");
			galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(galleryIntent, getString(R.string.select_picture)), REQUEST_IMAGE_SELECT);
			return true;
		case R.id.action_take_picture_with_camera:
//			Log.d (APPNAME, "Menu-Option Camera selected");
			createPathsAndFilenames();
			if (checkForCamera()) {
				Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
				startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
			}
			else {
				Toast.makeText(this, R.string.no_camera_found, Toast.LENGTH_SHORT).show();
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);		
		}
	}

	/**
	 * Receives the data from other activities (i.e. image_capture, gallery) 
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//		Log.d(APPNAME, "onActivityResult started");
		if (requestCode == REQUEST_IMAGE_CAPTURE) {
			if (resultCode == RESULT_OK) {
				decodeImage();
				setControls();
			}
			else if (resultCode == RESULT_CANCELED) {
				Toast.makeText(this, R.string.canceled, Toast.LENGTH_SHORT).show();
			} 
		}
		else if (requestCode == REQUEST_IMAGE_SELECT) {
			if (resultCode == RESULT_OK) {
				imageUri = data.getData(); // contains the file, which we want edit. important
				decodeImage();
				setControls();
			}
			else if (resultCode == RESULT_CANCELED) {
				Toast.makeText(this, R.string.canceled, Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	/**
	 * This method decodes the Bitmaps and set it to ImageView
	 * This is beeing done in a background-task (BitmapDecodeTask)
	 */
	private void decodeImage() {
//		Log.d(APPNAME, "decodeImage started");
		try {
			// free memory for gc
			if (currentImage!=null) {
				currentImage=null;
				resultImage=null;
			}
			BitmapDecodeTask bdt = new BitmapDecodeTask(this, mImageView);
			bdt.execute(this.getContentResolver().openInputStream(imageUri),imageFileName);
		}
		catch (FileNotFoundException e){
			Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
		}
	}
	
	/**
	 * This method shows the Controls on the UI after decoding a bitmap
	 */
	private void setControls() {
//		Log.d(APPNAME, "setControls started");
		// set xy-Start-Values for the image (necessary for pan-function)
		imageStartX = mImageView.getScaleX();
		imageStartY = mImageView.getScaleY();

		setListeners();

		selectedTool = 0;
		mSpinner.setSelection(selectedTool);
		seekBarValue = SEEKBARINITIALVALUE;
		mSeekBar.setProgress(seekBarValue);
		mSeekBar.setVisibility(View.INVISIBLE);
		undoButton.setVisibility(View.INVISIBLE);
		redoButton.setVisibility(View.INVISIBLE);
		acceptButton.setVisibility(View.INVISIBLE);
		controls.setVisibility(View.VISIBLE);

		Toast.makeText(this, R.string.select_tool, Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * This function shows for every tool the needed controls.
	 */
	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		selectedTool = arg2;
//		Log.d(APPNAME, "Spinner Item clicked. Selected tool: "+selectedTool);
		if (selectedTool==0) {
			mTextViewExtraInfo.setVisibility(View.INVISIBLE);
			mTextViewExtraInfo.setText(R.string.empty);
			mSeekBar.setVisibility(View.INVISIBLE);
			acceptButton.setVisibility(View.INVISIBLE);
			mRadioButtonVertical.setVisibility(View.INVISIBLE);
			mRadioButtonHorizontal.setVisibility(View.INVISIBLE);
			mCheckBox.setVisibility(View.INVISIBLE);
		}
		else if (selectedTool==6) {
			mTextViewExtraInfo.setVisibility(View.INVISIBLE);
			mTextViewExtraInfo.setText(R.string.empty);
			mSeekBar.setVisibility(View.INVISIBLE);
			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
			undoButton.setVisibility(View.INVISIBLE);
			acceptButton.setVisibility(View.VISIBLE);
			mRadioButtonVertical.setVisibility(View.INVISIBLE);
			mRadioButtonHorizontal.setVisibility(View.INVISIBLE);
			mCheckBox.setVisibility(View.INVISIBLE);
			Toast.makeText(this, R.string.press_accept_button, Toast.LENGTH_SHORT).show();
		}
		else if (selectedTool==7) {
			mTextViewExtraInfo.setVisibility(View.INVISIBLE);
			mTextViewExtraInfo.setText(R.string.empty);
			mSeekBar.setVisibility(View.INVISIBLE);
			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
			undoButton.setVisibility(View.INVISIBLE);
			acceptButton.setVisibility(View.VISIBLE);
			mRadioButtonVertical.setVisibility(View.INVISIBLE);
			mRadioButtonHorizontal.setVisibility(View.INVISIBLE);
			mCheckBox.setVisibility(View.INVISIBLE);
			Toast.makeText(this, R.string.press_accept_button, Toast.LENGTH_SHORT).show();
		}
		else if (selectedTool==8) {
			mTextViewExtraInfo.setVisibility(View.INVISIBLE);
			mTextViewExtraInfo.setText(R.string.empty);
			mSeekBar.setVisibility(View.INVISIBLE);
			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
			undoButton.setVisibility(View.INVISIBLE);
			acceptButton.setVisibility(View.VISIBLE);
			mRadioButtonVertical.setVisibility(View.INVISIBLE);
			mRadioButtonHorizontal.setVisibility(View.INVISIBLE);
			mCheckBox.setVisibility(View.INVISIBLE);
			Toast.makeText(this, R.string.press_accept_button, Toast.LENGTH_SHORT).show();
		}
		else if (selectedTool==9) {
			mTextViewExtraInfo.setVisibility(View.VISIBLE);
			seekBarValue = SEEKBARMAXVALUE;
			mSeekBar.setProgress(seekBarValue);
			mSeekBar.setVisibility(View.VISIBLE);
			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
			undoButton.setVisibility(View.INVISIBLE);
			acceptButton.setVisibility(View.VISIBLE);
			mRadioButtonVertical.setVisibility(View.VISIBLE);
			mRadioButtonVertical.setChecked(true);
			mRadioButtonHorizontal.setVisibility(View.VISIBLE);
			mCheckBox.setVisibility(View.VISIBLE);
			mCheckBox.setText(R.string.cb_removeSelected);
			Toast.makeText(this, R.string.set_options_and_press_accept_button, Toast.LENGTH_SHORT).show();
		}
		else {
			seekBarValue = SEEKBARINITIALVALUE;
			mSeekBar.setProgress(seekBarValue);
			mSeekBar.setVisibility(View.VISIBLE);
			mRadioButtonVertical.setVisibility(View.INVISIBLE);
			mRadioButtonHorizontal.setVisibility(View.INVISIBLE);
			mCheckBox.setVisibility(View.INVISIBLE);
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
	}
	
	/**
	 * Only maps the progress of the seekBar to "seekBarValue"
	 */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		seekBarValue = progress;
	}
	
	/**
	 * When start moving the seekBar, the undo and accept-Buttons
	 * will be displayed
	 */
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		seekBarUndoValue = seekBarValue;
		undoButton.setVisibility(View.VISIBLE);
		acceptButton.setVisibility(View.VISIBLE);
		redoButton.setVisibility(View.INVISIBLE);
	}

	/**
	 * Depending of the selected tool, a specified action will be started.
	 * Except "selectedTool==9" the Bitmap will be manipulated directly with
	 * the ImageManipulator-Class
	 */
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		if (selectedTool ==9) {
			// for seamCarving (Tool==9) the accept button is used to start manipulation, so do not start here automatically
			mTextViewExtraInfo.setText(this.getResources().getString(R.string.seams_to_remove)+(seekBarValue-510)*-1);
		}
		else
		{
			if (selectedTool==2) {
				if (seekBarValue<=170) {
					//red
					mTextViewExtraInfo.setText(this.getResources().getString(R.string.chosen_color_red));
				}
				else if (seekBarValue>170 && seekBarValue<=340) {
					//green
					mTextViewExtraInfo.setText(this.getResources().getString(R.string.chosen_color_green));
				}
				else if (seekBarValue>341) {
					//blue
					mTextViewExtraInfo.setText(this.getResources().getString(R.string.chosen_color_blue));
				}
				mTextViewExtraInfo.setVisibility(View.VISIBLE);
			}

			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
//			Log.d(APPNAME, "onStopTrackingTouch: seekBarUndoValue: " + seekBarUndoValue + " seekBarValue: "+ seekBarValue);
			ImageManipulator im = new ImageManipulator(this, mImageView);
			im.execute(selectedTool,seekBarValue,currentImage);
		}
	}
	
	/**
	 * Depending on the selected tool (Spinner) a click will save the image
	 * in the cache (for further manipulation) or starts the manipulation for tools
	 * after the right parameters set by the user.
	 * @param view
	 */
	public void acceptButtonPressed(View view) {
//		Log.d(APPNAME, "acceptButtonPressed. selectedTool: "+selectedTool);
		if (selectedTool == 9) {
			// set default values for seam-carving
			int direction=1;
			boolean removeSeams=false;
			if (mRadioButtonHorizontal.isChecked()) {
				direction=2;
			}
			if (mCheckBox.isChecked()) {
				removeSeams=true;
			}
			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
			ImageManipulator im = new ImageManipulator(this, mImageView);
			im.execute(selectedTool,seekBarValue,currentImage,direction,removeSeams,startX, startY, stopX, stopY);
			undoButton.setVisibility(View.VISIBLE);
		}
		else if (selectedTool >=6 & selectedTool <=8) {
			saveImage(currentImage, imageCachePath, imageUndoFileName, false);
			addBitmapToMemoryCache(imageUndoFileName, currentImage);
			ImageManipulator im = new ImageManipulator(this, mImageView);
			im.execute(selectedTool,seekBarValue,currentImage);
			undoButton.setVisibility(View.VISIBLE);
		}
		else if (selectedTool > 0 & selectedTool < 6) {
			setCurrentImage(resultImage);
			Toast.makeText(this, R.string.changes_applied, Toast.LENGTH_SHORT).show();
		}
		else {
			// no tool is selected, so the user is asked for one...
			Toast.makeText(this, R.string.select_tool_first, Toast.LENGTH_SHORT).show();
		}
	}
	
	/**
	 * Allows the user to restore the bitmap after one manipulation
	 * if the manipulation was not successful (i.e. OutOfMemoryError)
	 * the controls will be reset
	 * @param view
	 */
	public void undoButtonPressed(View view) {
		if (!taskWasSuccessful) {
			onBackPressed();
		}
		else {
//			Log.d(APPNAME, "undoButtonPressed: selectedTool: "+selectedTool+" seekBarRedoValue: " + seekBarRedoValue + " seekBarValue: "+ seekBarValue);
			seekBarRedoValue = seekBarValue;
			loadImageAndSetToScreen(imageCachePath,imageUndoFileName);
			mSeekBar.setProgress(seekBarUndoValue);
			undoButton.setVisibility(View.INVISIBLE);
			redoButton.setVisibility(View.VISIBLE);
			if (selectedTool<6) {
				saveImage(resultImage, imageCachePath, imageRedoFileName, false);
				addBitmapToMemoryCache(imageRedoFileName, resultImage);
			}
			else {
				saveImage(currentImage, imageCachePath, imageRedoFileName, false);
				addBitmapToMemoryCache(imageRedoFileName, currentImage);
			}
		}
	}
	
	/**
	 * Allows the user to restore the bitmap with the manipulation when he 
	 * pressed the undo-button before
	 * @param view
	 */
	public void redoButtonPressed(View view) {
//		Log.d(APPNAME, "redoButtonPressed. selectedTool: "+selectedTool);
		loadImageAndSetToScreen(imageCachePath,imageRedoFileName);
		mSeekBar.setProgress(seekBarRedoValue);
		undoButton.setVisibility(View.VISIBLE);
		redoButton.setVisibility(View.INVISIBLE);
	}
	
	/**
	 * Reset the Controls and the ImageView to the last saved image (accept-button)
	 */
	@Override
	public void onBackPressed() {
//		Log.d(APPNAME, "Back-Button pressed");
		if (currentImage != null) {
			mImageView.setImageBitmap(currentImage);
			setControls();
		}
		else {
			super.onBackPressed();
		}
	}
	
	/**
	 * Set a bitmap to "currentImage"
	 * @param bitmap
	 */
	public static void setCurrentImage(Bitmap bitmap) {
		currentImage = bitmap;
	}
	
	/**
	 * Set a bitmap to "resultImage"
	 * @param bitmap
	 */
	public static void setResultImage(Bitmap bitmap) {
		resultImage = bitmap;
	}
	
	/**
	 * Set "taskWasSuccessful" to true or false. It is used
	 * by the undoButton to recognize if the previous manipulation was successful
	 * @param bitmap
	 */
	public static void setTaskWasSuccessful(boolean b) {
		taskWasSuccessful = b;
	}

}
