package net.thesysadmin.nebba;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;


/**
 * This Class saves the bitmap in the specified path.
 * It uses AsyncTask to do this in the background.
 * In case of an error, it will display an alert-dialog (error)
 * @author Martin Mueller, 8881847
 *
 */
public class BitmapSaveTask extends AsyncTask<Object, Void, Boolean>{

	private static final String APPNAME = "NEBBA";
	private boolean saveWasSuccessfull = false;
	private Context myContext;
	private boolean persistent = false;
	private String filename;
	
	
	public BitmapSaveTask(Context context) {
		myContext = context;
	}
	
	@Override
	protected Boolean doInBackground(Object... params) {
		
		Bitmap bitmap = (Bitmap) params[0];
		String path = (String) params[1];
		filename = (String) params[2];
		persistent = (Boolean) params[3];
		
		Log.d(APPNAME, "saveImage: Start saving Image: " + path + File.separator + filename);
		
		if (path != null) {
			String mImageFile = path + File.separator + filename;
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(mImageFile);
				BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
				bitmap.compress(CompressFormat.JPEG, 100, bufferedOutputStream);
				bufferedOutputStream.flush();
				bufferedOutputStream.close();
			} catch (FileNotFoundException e) {
				Log.d(APPNAME, "Error saving file: " + e.getMessage());
				saveWasSuccessfull = false;
			} catch (IOException e) {
				Log.d(APPNAME, "Error saving file: " + e.getMessage());
				saveWasSuccessfull = false;
			}
			Log.d(APPNAME, "Save was successful");
			saveWasSuccessfull = true;
		}
		return saveWasSuccessfull;
	}
	
	protected void onPostExecute(Boolean successfull) {
		if (!successfull) {
			showErrorDialog();
		}
		if (persistent && successfull) {
			Log.d(APPNAME, "File successfully saved permanently");
			Toast.makeText(myContext, myContext.getString(R.string.picture_saved_in_file)+" "+filename, Toast.LENGTH_SHORT).show();
		}
		// rescan media, 
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			myContext.sendBroadcast (new Intent(Intent.ACTION_MEDIA_MOUNTED,Uri.parse("file://" + Environment.getExternalStorageDirectory())));
		}
		else {
			myContext.sendBroadcast (new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.parse("file://" + Environment.getExternalStorageDirectory())));
		}
		
	}

	private void showErrorDialog() {
		new AlertDialog.Builder(myContext)
		.setTitle(myContext.getResources().getString(R.string.error))
		.setMessage(myContext.getResources().getString(R.string.error_saving_picture))
		.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) { 

			}
		})
		.setIcon(android.R.drawable.ic_dialog_alert)
		.show();
	}

}
