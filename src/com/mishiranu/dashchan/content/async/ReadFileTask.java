package com.mishiranu.dashchan.content.async;

import android.content.Context;
import android.net.Uri;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.util.DataFile;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ReadFileTask extends HttpHolderTask<String, Long, Boolean> {
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 15000;

	public interface Callback {
		void onStartDownloading();
		void onFinishDownloading(boolean success, Uri uri, DataFile file, ErrorItem errorItem);
		default void onCancelDownloading() {}
		void onUpdateProgress(long progress, long progressMax);
	}

	public interface FileCallback extends Callback {
		void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem);

		@Override
		default void onFinishDownloading(boolean success, Uri uri, DataFile file, ErrorItem errorItem) {
			File realFile = file.getFileOrUri().first;
			if (realFile == null) {
				throw new IllegalStateException();
			}
			onFinishDownloading(success, uri, realFile, errorItem);
		}
	}

	private final Callback callback;
	private final String chanName;
	private final Uri fromUri;
	private final DataFile toFile;
	private final File cachedMediaFile;
	private final boolean overwrite;

	private ErrorItem errorItem;

	private boolean loadingStarted;

	private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
		@Override
		public void onProgressChange(long progress, long progressMax) {
			publishProgress(progress, progressMax);
		}
	};

	public static ReadFileTask createCachedMediaFile(Context context, FileCallback callback, String chanName,
			Uri fromUri, File cachedMediaFile) {
		DataFile toFile = DataFile.obtain(context, DataFile.Target.CACHE, cachedMediaFile.getName());
		return new ReadFileTask(callback, chanName, fromUri, toFile, null, true);
	}

	public static ReadFileTask createShared(Callback callback, String chanName,
			Uri fromUri, DataFile toFile, boolean overwrite) {
		File cachedMediaFile = CacheManager.getInstance().getMediaFile(fromUri, true);
		if (cachedMediaFile == null || !cachedMediaFile.exists() ||
				CacheManager.getInstance().cancelCachedMediaBusy(cachedMediaFile)) {
			cachedMediaFile = null;
		}
		return new ReadFileTask(callback, chanName, fromUri, toFile, cachedMediaFile, overwrite);
	}

	private ReadFileTask(Callback callback, String chanName, Uri fromUri, DataFile toFile,
			File cachedMediaFile, boolean overwrite) {
		this.callback = callback;
		this.chanName = chanName;
		this.fromUri = fromUri;
		this.toFile = toFile;
		this.cachedMediaFile = cachedMediaFile;
		this.overwrite = overwrite;
	}

	@Override
	public void onPreExecute() {
		callback.onStartDownloading();
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, String... params) {
		boolean success = false;
		try {
			loadingStarted = true;
			// noinspection StatementWithEmptyBody
			if (!overwrite && toFile.exists()) {
				// Do nothing
			} else if (cachedMediaFile != null) {
				progressHandler.setInputProgressMax(cachedMediaFile.length());
				try (FileInputStream input = new FileInputStream(cachedMediaFile);
						OutputStream output = toFile.openOutputStream()) {
					IOUtils.copyStream(input, output, progressHandler);
				} catch (IOException e) {
					ErrorItem.Type type = getErrorTypeFromExceptionAndHandle(e);
					errorItem = new ErrorItem(type != null ? type : ErrorItem.Type.UNKNOWN);
					return false;
				}
			} else {
				Uri uri = fromUri;
				HttpResponse response;
				String chanName = this.chanName;
				if (chanName == null) {
					chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
				}
				if (chanName != null) {
					ChanPerformer.ReadContentResult result = ChanPerformer.get(chanName).safe()
							.onReadContent(new ChanPerformer.ReadContentData(uri,
									CONNECT_TIMEOUT, READ_TIMEOUT, holder, -1, -1));
					response = result != null ? result.response : null;
				} else {
					response = new HttpRequest(uri, holder).setTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT).perform();
				}
				if (response == null) {
					errorItem = new ErrorItem(ErrorItem.Type.DOWNLOAD);
					return false;
				}
				progressHandler.setInputProgressMax(response.getLength());
				try (InputStream input = response.open();
						OutputStream output = toFile.openOutputStream()) {
					IOUtils.copyStream(input, output, progressHandler);
				} catch (IOException e) {
					ErrorItem.Type errorType = getErrorTypeFromExceptionAndHandle(e);
					if (errorType != null) {
						errorItem = new ErrorItem(errorType);
						return false;
					} else {
						throw response.fail(e);
					}
				} finally {
					response.cleanupAndDisconnect();
				}
			}
			success = true;
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			if (!success) {
				toFile.delete();
			}
			File file = toFile.getFileOrUri().first;
			if (file != null) {
				CacheManager.getInstance().handleDownloadedFile(file, success);
			}
		}
	}

	public static ErrorItem.Type getErrorTypeFromExceptionAndHandle(IOException exception) {
		if (exception instanceof FileNotFoundException) {
			Log.persistent().stack(exception);
			return ErrorItem.Type.NO_ACCESS_TO_MEMORY;
		} else {
			String message = exception.getMessage();
			if (message != null && message.contains("ENOSPC")) {
				Log.persistent().stack(exception);
				return ErrorItem.Type.INSUFFICIENT_SPACE;
			}
		}
		return null;
	}

	@Override
	public void onPostExecute(Boolean success) {
		callback.onFinishDownloading(success, fromUri, toFile, errorItem);
	}

	@Override
	protected void onProgressUpdate(Long... values) {
		callback.onUpdateProgress(values[0], values[1]);
	}

	public boolean isDownloadingFromCache() {
		return cachedMediaFile != null;
	}

	public String getFileName() {
		return toFile.getName();
	}

	@Override
	public void cancel() {
		super.cancel();

		if (loadingStarted) {
			toFile.delete();
			File file = toFile.getFileOrUri().first;
			if (file != null) {
				CacheManager.getInstance().handleDownloadedFile(file, false);
			}
		}
		callback.onCancelDownloading();
	}
}
