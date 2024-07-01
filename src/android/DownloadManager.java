package com.dhieu;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;


public class DownloadManager extends CordovaPlugin {
    private static final String DOWNLOAD_CHANNEL = "DOWNLOAD";
    private NotificationManager notificationManager;
    private ActivityResultLauncher<Intent> openFileLauncher;
    private static final String LOG_TAG = "DownloadManager";
    private static final String PERCENT = "percent";
    private static final String URL = "url";

    @Override
    protected void pluginInitialize() {
        setUpNotificationChannel();
        openFileLauncher = cordova.getActivity().registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
            }
        );
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("download".equals(action)) {
            String urlString = args.getString(0);
            String fileName = args.getString(1);
            String des = args.getString(2);
            cordova.getThreadPool().execute(
                () -> startDownload(
                    fileName,
                    urlString,
                    callbackContext
                )
            );
            return true;
        }
        if ("open".equals(action)) {
            String uriString = args.getString(0);
            Uri uri = Uri.parse(uriString);
            cordova.getThreadPool().execute(
                    () -> openFileLauncher.launch(intentOpenFile(uri))
            );

            return true;
        }
        return false;
    }

    private Uri mediaUri(String fileName) {
        ContentResolver resolver = cordova.getActivity().getApplicationContext().getContentResolver();
        Uri downloadUri;
        String mimeType = getMimeType(fileName);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (mimeType.startsWith("image")) {
                downloadUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else if (mimeType.startsWith("video")) {
                downloadUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else if (mimeType.startsWith("audio")) {
                downloadUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                downloadUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
            }
        } else {
            if (mimeType.startsWith("image")) {
                downloadUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if (mimeType.startsWith("video")) {
                downloadUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if (mimeType.startsWith("audio")) {
                downloadUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            } else {
                downloadUri = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
            }
        }
        ContentValues file = new ContentValues();
        file.put("_display_name", fileName);
        return resolver.insert(downloadUri, file);
    }

    private String getMimeType(String fileName) {
        int index = fileName.lastIndexOf(".");
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substring(index + 1));
    }

    private void setUpNotificationChannel() {
        notificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(DOWNLOAD_CHANNEL) == null) {
                notificationManager.createNotificationChannel(
                    new NotificationChannel(
                        DOWNLOAD_CHANNEL,
                        "Download",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                );
            }
        }
    }

    private void startDownload(String fileName, String urlString, CallbackContext callbackContext) {
        if (urlString != null && urlString.length() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                File tempFile = createTempFile(fileName);
                int notifyId = Objects.hash(fileName, urlString, Instant.now());
                createNotify(notifyId, fileName);
                URL url = new URL(urlString);
                URLConnection conexion = url.openConnection();
                conexion.connect();
                int lengthOfFile = conexion.getContentLength();
                byte[] data = new byte[1024];
                int total = 0;
                int count;
                try (
                    InputStream input = new BufferedInputStream(url.openStream());
                    OutputStream output = Files.newOutputStream(tempFile.toPath(), StandardOpenOption.CREATE)
                ) {
                    Instant notifyTime = updateNotifyProgress(notifyId, fileName, lengthOfFile, total, null);
                    JSONObject progress = new JSONObject();
                    progress.put(PERCENT, 0);
                    progress.put(URL, null);
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        notifyTime = updateNotifyProgress(notifyId, fileName, lengthOfFile, total, notifyTime);
                        progress.put(PERCENT, (float) total / lengthOfFile);
                        PluginResult result = new PluginResult(
                            PluginResult.Status.OK,
                            progress
                        );
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                        output.write(data, 0, count);
                    }
                    Uri uri = mediaUri(fileName);
                    try (
                        OutputStream fileOutput = cordova.getActivity().getApplicationContext().getContentResolver().openOutputStream(uri);
                        InputStream fileInput = Files.newInputStream(tempFile.toPath())
                    ) {
                        copy(fileInput, fileOutput);
                        Files.delete(tempFile.toPath());
                        progress.put(PERCENT, 1);
                        progress.put(URL, uri.toString());
                        callbackContext.success(progress);
                        notifyCompleted(notifyId, uri, fileName);
                    }
                } catch (Exception e) {
                    notifyError(notifyId, fileName);
                    Files.delete(tempFile.toPath());
                    throw e;
                }
            } catch (AccessDeniedException e) {
                callbackContext.error("AccessDeniedException");
            } catch (Exception e) {
                LOG.d(LOG_TAG, "download error " + e.getMessage());
                callbackContext.error("Expected one non-empty string argument.");
            }
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private File createTempFile(String fileName) throws IOException {
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > -1) {
            return File.createTempFile(fileName.substring(0, dotIndex), fileName.substring(dotIndex), cordova.getActivity().getCacheDir());
        }
        return File.createTempFile(fileName, null, cordova.getActivity().getCacheDir());
    }

    private void createNotify(int id, String fileName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext(), DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.window_icon)
            .setContentTitle("Downloading " + fileName)
            .setOnlyAlertOnce(true);
        notificationManager.notify(id, builder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Instant updateNotifyProgress(int id, String fileName, int max, int progress, Instant notifyTime) {
        if (notifyTime == null || notifyTime.isBefore(Instant.now().minusSeconds(1))) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext(), DOWNLOAD_CHANNEL)
                    .setSmallIcon(R.drawable.window_icon)
                    .setContentTitle(fileName)
                    .setOnlyAlertOnce(true)
                    .setProgress(max, progress, false);
            notificationManager.notify(id, builder.build());
            return Instant.now();
        }
        return notifyTime;
    }

    private void notifyCompleted(int id, Uri uri, String fileName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext(), DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.window_icon)
            .setContentTitle("Completed download " + fileName)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    cordova.getActivity().getApplicationContext(),
                    2,
                    intentOpenFile(uri),
                    PendingIntent.FLAG_MUTABLE
                )
            );
        notificationManager.notify(id, builder.build());
    }

    private void notifyError(int id, String fileName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext(), DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.window_icon)
            .setContentTitle("Download " + fileName + " error!");
        notificationManager.notify(id, builder.build());
    }

    private Intent intentOpenFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        ContentResolver contentResolver = cordova.getContext().getContentResolver();
        String mimeType = contentResolver.getType(uri);
        intent.setDataAndType(
            uri, mimeType != null ? mimeType : "*/*"
        );
        if (intent.resolveActivity(this.cordova.getActivity().getPackageManager()) == null) {
            intent.setDataAndType(
                    uri, "*/*"
            );
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

}
