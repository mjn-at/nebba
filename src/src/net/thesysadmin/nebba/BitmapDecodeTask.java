package net.thesysadmin.nebba;

import java.io.InputStream;
import java.lang.ref.WeakReference;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * This class decodes the bitmap from the specified path.
 * In case of an error (i.e. OutOfMemoryError) it will display an alert-dialog and
 * set a demo-bitmap to the ImageView, so that the user can try the manipulation-tools
 * @author Martin Mueller, 8881847
 *
 */
public class BitmapDecodeTask extends AsyncTask<Object, Void, Bitmap>{
	
	private static final String APPNAME = "NEBBA";
	private final WeakReference<ImageView> imageViewReference;
	private InputStream is;
	private String key;
	private Boolean taskWasSuccessful=true;
	private Context myContext;
	
	ProgressDialog mProgressDialog = null;
	
	public BitmapDecodeTask(Context context, ImageView imageView) {
		myContext = context;
		imageViewReference = new WeakReference<ImageView>(imageView);
	}

	@Override
	protected void onPreExecute() {
		mProgressDialog = new ProgressDialog(myContext);
		mProgressDialog.setTitle(myContext.getResources().getString(R.string.please_wait));
		mProgressDialog.setMessage(myContext.getResources().getString(R.string.loading_bitmap));       
		mProgressDialog.setIndeterminate(false);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(false);
		mProgressDialog.setCanceledOnTouchOutside(false);
		mProgressDialog.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				Toast.makeText(myContext, R.string.canceled, Toast.LENGTH_SHORT).show();
				
			}
		});
		mProgressDialog.show();
	}
	
	@Override
	protected Bitmap doInBackground(Object... params) {
		is = (InputStream) params[0];
		key = (String) params[1];
		Bitmap bitmap=null;
		Log.d (APPNAME, "ASyncTask BitmapDecodeTask started for key: "+key);
		try {
			BitmapFactory.Options options = new BitmapFactory.Options(); 
			options.inPurgeable = true;
			bitmap = BitmapFactory.decodeStream(is, null, options);
			taskWasSuccessful= true;
		}
		catch (OutOfMemoryError oome) {
			bitmap = null;
			bitmap = BitmapFactory.decodeResource(myContext.getResources(), R.drawable.demo_image);
			taskWasSuccessful = false;
		}
		StartActivity.addBitmapToMemoryCache(key, bitmap);
		return bitmap;
	}
	
	@Override
	protected void onPostExecute(Bitmap result) {
		if (!taskWasSuccessful) {
			showOOEMDialog();
		}
		
		if (imageViewReference != null && result != null) {
			final ImageView imageView = imageViewReference.get();
			StartActivity.setCurrentImage(result);
			StartActivity.setResultImage(result);
			imageView.setImageBitmap(result);
		}
		
		mProgressDialog.dismiss();
		mProgressDialog = null;
	}
	
	private void showOOEMDialog() {
		new AlertDialog.Builder(myContext)
	    .setTitle(myContext.getResources().getString(R.string.error))
	    .setMessage(myContext.getResources().getString(R.string.error_oome_default_load))
	    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) { 

	        }
	     })
	    .setIcon(android.R.drawable.ic_dialog_alert)
	    .show();
	}

}
