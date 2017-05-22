/*
 * Copyright 2017 Srihari Yachamaneni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package demo.moveinsync.com.appsignin;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.DOWNLOAD_SERVICE;

/**
 * Lists all files available to download by making network calls using {@link ContactSenderAPITask}
 * <p>
 * Functionalities include:
 * <ul>
 * <li>Adds file downloads to {@link DownloadManager}'s Queue</li>
 * * <li>Checks Sender API availability and throws error after certain retry limit</li>
 * </ul>
 * <p>
 * Created by Sri on 21/12/16.
 */

public class FilesListingFragment {
    private static final String TAG = "AppSignin FilesListing";

    public static final String PATH_FILES = "http://%s:%s/files";
    public static final String PATH_STATUS = "http://%s:%s/status";
    public static final String PATH_FILE_DOWNLOAD = "http://%s:%s/file/%s";

    private ContactSenderAPITask mUrlsTask;
    private ContactSenderAPITask mStatusCheckTask;

    private static String mPort, mSenderName, mSenderIp, mSenderSSID;

    static final int CHECK_SENDER_STATUS = 100;
    static final int SENDER_DATA_FETCH = 101;

    private static Context appContext;

    private String fileToDownload;

    private volatile boolean isFileListingDone = false;

    private static final int SENDER_DATA_FETCH_RETRY_LIMIT = 3;
    private int senderDownloadsFetchRetry = SENDER_DATA_FETCH_RETRY_LIMIT, senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;

    public void onCreateView() {
        fetchSenderFiles();
    }

    public static FilesListingFragment getInstance(Context appContext, String senderIp, String ssid, String senderName, String port) {
        FilesListingFragment fragment = new FilesListingFragment();
        FilesListingFragment.mSenderIp = senderIp;
        FilesListingFragment.mSenderName = senderName;
        FilesListingFragment.mSenderSSID = ssid;
        FilesListingFragment.mPort = port;
        FilesListingFragment.appContext = appContext;
        return fragment;
    }

    private void fetchSenderFiles() {
        if (null != mUrlsTask)
            mUrlsTask.cancel(true);
        mUrlsTask = new ContactSenderAPITask(SENDER_DATA_FETCH);
        mUrlsTask.execute(String.format(PATH_FILES, mSenderIp, mPort));
    }

    private void checkSenderAPIAvailablity() {
        if (null != mStatusCheckTask)
            mStatusCheckTask.cancel(true);
        mStatusCheckTask = new ContactSenderAPITask(CHECK_SENDER_STATUS);
        mStatusCheckTask.execute(String.format(PATH_STATUS, mSenderIp, mPort));
    }

    public String getSenderSSID() {
        return mSenderSSID;
    }

    public String getSenderIp() {
        return mSenderIp;
    }

    private void loadListing(String contentAsString) {
        Log.d(TAG, "Content: "+contentAsString);
        Type collectionType = new TypeToken<List<String>>() {
        }.getType();
        ArrayList<String> files = new Gson().fromJson(contentAsString, collectionType);
        Log.d(TAG, "Numfiles = "+files.size());
        if (null == files || files.size() == 0) {
            Log.d(TAG, "No Downloads found.\n Tap to Retry");
        } else {
            fileToDownload = files.get(0);
        }
        Log.d(TAG, "File to download: "+fileToDownload);
        postDownloadRequestToDM(Uri.parse(String.format(PATH_FILE_DOWNLOAD, mSenderIp, mPort, 0)), "BMTCWomenPassengerSecurity.apk");
        Toast.makeText(appContext, "Downloading " + fileToDownload + "...", Toast.LENGTH_SHORT).show();
    }

    private void onDataFetchError() {
        Log.d(TAG, "Error occurred while fetching data.\n Tap to Retry");
    }

    private long postDownloadRequestToDM(Uri uri, String fileName) {

        // Create request for android download manager
        DownloadManager downloadManager = (DownloadManager) appContext.getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(uri);

        //Setting title of request
        request.setTitle(fileName);

        //Setting description of request
        request.setDescription("ShareThem");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        //Set the local destination for the downloaded file to a path
        //within the application's external files directory
        request.setDestinationInExternalFilesDir(appContext,
                Environment.DIRECTORY_DOWNLOADS, fileName);

        //Enqueue download and save into referenceId
        Log.d(TAG, "Saving into: "+Environment.DIRECTORY_DOWNLOADS);
        return downloadManager.enqueue(request);
    }

    /**
     * Performs network calls to fetch data/status from Sender.
     * Retries on error for times bases on values of {@link FilesListingFragment#senderDownloadsFetchRetry}
     */
    private class ContactSenderAPITask extends AsyncTask<String, Void, String> {

        int mode;
        boolean error;

        ContactSenderAPITask(int mode) {
            this.mode = mode;
        }

        @Override
        protected String doInBackground(String... urls) {
            error = false;
            try {
                return downloadDataFromSender(urls[0]);
            } catch (IOException e) {
                e.printStackTrace();
                error = true;
                Log.e(TAG, "Exception: " + e.getMessage());
                return null;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            switch (mode) {
                case SENDER_DATA_FETCH:
                    if (error) {
                        if (senderDownloadsFetchRetry >= 0) {
                            --senderDownloadsFetchRetry;
                            Log.d(TAG, "Retires = " + senderDownloadsFetchRetry);
                            return;
                        } else senderDownloadsFetchRetry = SENDER_DATA_FETCH_RETRY_LIMIT;
                        onDataFetchError();
                    } else {
                        loadListing(result);
                        Log.d(TAG, "File listing is done");
                        isFileListingDone = true;
                    }
                    break;
                case CHECK_SENDER_STATUS:
                    if (error) {
                        if (senderStatusCheckRetryLimit > 1) {
                            --senderStatusCheckRetryLimit;
                            Log.d(TAG, "SenderStatusCheckRetryLimit: " + senderStatusCheckRetryLimit);
                        } else {
                            senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;
                            Toast.makeText(appContext, "Receiver error. Sender disconnected.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Check sender status.");
                    }
                    break;
            }

        }

        private String downloadDataFromSender(String apiUrl) throws IOException {
            InputStream is = null;
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                // Starts the query
                conn.connect();
//                int response =
                conn.getResponseCode();
//                Log.d(TAG, "The response is: " + response);
                is = conn.getInputStream();
                // Convert the InputStream into a string
                return readIt(is);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        private String readIt(InputStream stream) throws IOException {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(
                        new InputStreamReader(stream, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                stream.close();
            }
            return writer.toString();
        }
    }

    private static class UiUpdateHandler extends Handler {
        WeakReference<FilesListingFragment> mFragment;

        UiUpdateHandler(FilesListingFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            FilesListingFragment fragment = mFragment.get();
            if (null == mFragment)
                return;
            switch (msg.what) {
                case CHECK_SENDER_STATUS:
                    fragment.checkSenderAPIAvailablity();
                    break;
                case SENDER_DATA_FETCH:
                    fragment.fetchSenderFiles();
                    break;
            }
        }
    }

}
