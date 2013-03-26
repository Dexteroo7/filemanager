package org.openintents.filemanager.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.openintents.filemanager.R;
import org.openintents.filemanager.dialogs.SingleCompressDialog;
import org.openintents.filemanager.files.FileHolder;
import org.openintents.filemanager.lists.FileListFragment;
import org.openintents.filemanager.lists.SimpleFileListFragment;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;

public class CompressManager extends DialogFragment {
	/**
	 * TAG for log messages.
	 */
	static final String TAG = "CompressManager";

	private static final int BUFFER_SIZE = 1024;
	private ProgressDialog progressDialog;
	private int fileCount;
	private String fileOut;
	private OnCompressFinishedListener onCompressFinishedListener = null;
	private CompressTask mCompressTask;
	private List<FileHolder> listToCompress;
	public File tbcreated;

	public void setTask() {

		Log.e("CompressManager", "setTask");
		mCompressTask = new CompressTask();
		mCompressTask.setFragment(this);
	}

	public void compress(FileHolder f, String out) {

		Log.e("CompressManager", "compress");
		List<FileHolder> list = new ArrayList<FileHolder>(1);
		list.add(f);
		compress(list, out);
	}

	public void compress(List<FileHolder> list, String out) {

		Log.e("CompressManager", "compress");
		if (list.isEmpty()) {
			Log.v(TAG, "couldn't compress empty file list");
			return;
		}
		this.fileOut = list.get(0).getFile().getParent() + File.separator + out;
		fileCount = 0;
		for (FileHolder f : list) {
			fileCount += FileUtils.getFileCount(f.getFile());
		}
		listToCompress = list;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {

		Log.e("CompressManager", "onCreate");
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		tbcreated = SingleCompressDialog.tbcreated;
		mCompressTask.execute(listToCompress);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		this.setOnCompressFinishedListener(new CompressManager.OnCompressFinishedListener() {

			@Override
			public void compressFinished() {
				((FileListFragment) getTargetFragment()).refresh();

				MediaScannerUtils.informFileAdded(getTargetFragment()
						.getActivity().getApplicationContext(), tbcreated);
			}
		});
		Log.e("CompressManager", "onCreateDialog");
		progressDialog = new ProgressDialog(getTargetFragment().getActivity());
		progressDialog.setCancelable(false);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMessage(getTargetFragment().getActivity().getString(
				R.string.compressing));
		progressDialog.show();
		progressDialog.setProgress(0);
		return progressDialog;
	}

	@Override
	public void onDestroyView() {

		Log.e("CompressManager", "onDestroyView");
		if (progressDialog != null && getRetainInstance()) {
			progressDialog.setDismissMessage(null);
		}
		super.onDestroyView();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {

		Log.e("CompressManager", "onDismiss");
		super.onDismiss(dialog);
		if (mCompressTask != null)
			mCompressTask.cancel(false);
	}

	@Override
	public void onResume() {

		Log.e("CompressManager", "onResume");
		super.onResume();
		if (mCompressTask == null)
			progressDialog.dismiss();
	}

	public void updateProgress(int percent) {

		progressDialog.setProgress(percent);
		// progressDialog.setProgress(percent);
	}

	public void taskFinished(boolean success) {

		if (isResumed())
			dismiss();
		progressDialog.dismiss();
		mCompressTask = null;
		if (getTargetFragment() != null) {

			if (success == false)
				getTargetFragment().onActivityResult(
						SimpleFileListFragment.TASK_FRAGMENT,
						Activity.RESULT_CANCELED, null);
			else
				getTargetFragment().onActivityResult(
						SimpleFileListFragment.TASK_FRAGMENT,
						Activity.RESULT_OK, null);
		}
	}

	public class CompressTask extends
			AsyncTask<List<FileHolder>, Void, Integer> {
		private static final int success = 0;
		private static final int error = 1;
		private ZipOutputStream zos;
		CompressManager mCompressManager;
		int mProgress = 0;

		void setFragment(CompressManager compressManager) {

			Log.e("CompressTask", "setFragment");
			mCompressManager = compressManager;
		}

		/**
		 * count of compressed file to update the progress bar
		 */
		private int isCompressed = 0;

		/**
		 * Recursively compress file or directory
		 * 
		 * @returns 0 if successful, error value otherwise.
		 */
		private void compressFile(File file, String path) throws IOException {

			if (!file.isDirectory()) {
				byte[] buf = new byte[BUFFER_SIZE];
				int len;
				FileInputStream in = new FileInputStream(file);
				if (path.length() > 0)
					zos.putNextEntry(new ZipEntry(path + "/" + file.getName()));
				else
					zos.putNextEntry(new ZipEntry(file.getName()));
				while ((len = in.read(buf)) > 0) {
					zos.write(buf, 0, len);
				}
				in.close();
				return;
			}
			if (file.list() == null) {
				return;
			}
			for (String fileName : file.list()) {
				File f = new File(file.getAbsolutePath() + File.separator
						+ fileName);
				compressFile(f, path + File.separator + file.getName());
				isCompressed++;
				mProgress = ((isCompressed * 100) / fileCount);
				publishProgress();
			}
		}

		@Override
		protected void onPreExecute() {

			Log.e("CompressTask", "onPreExecute");
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(new File(fileOut));
				zos = new ZipOutputStream(new BufferedOutputStream(out));
			} catch (FileNotFoundException e) {
				Log.e(TAG, "error while creating ZipOutputStream");
			}
		}

		@Override
		protected Integer doInBackground(List<FileHolder>... params) {
			if (zos == null) {
				return error;
			}
			List<FileHolder> list = params[0];
			for (FileHolder file : list) {
				try {
					compressFile(file.getFile(), "");
				} catch (IOException e) {
					Log.e(TAG, "Error while compressing", e);
					return error;
				}
			}
			return success;
		}

		@Override
		protected void onProgressUpdate(Void... unused) {

			if (mCompressManager == null)
				return;
			mCompressManager.updateProgress(mProgress);
		}

		@Override
		protected void onPostExecute(Integer result) {
			try {
				zos.flush();
				zos.close();
			} catch (IOException e) {
				Log.e(TAG, "error while closing zos", e);
			} catch (NullPointerException e) {
				Log.e(TAG, "zos was null and couldn't be closed", e);
			}

			if (result == error) {
				mCompressManager.taskFinished(false);
			} else if (result == success) {
				mCompressManager.taskFinished(true);
			}

			if (onCompressFinishedListener != null)
				onCompressFinishedListener.compressFinished();
		}

	}

	public interface OnCompressFinishedListener {
		public abstract void compressFinished();
	}

	public CompressManager setOnCompressFinishedListener(
			OnCompressFinishedListener listener) {
		this.onCompressFinishedListener = listener;
		return this;
	}
}
