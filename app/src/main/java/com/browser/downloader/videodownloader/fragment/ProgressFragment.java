package com.browser.downloader.videodownloader.fragment;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.browser.downloader.videodownloader.R;
import com.browser.downloader.videodownloader.adapter.ProgressAdapter;
import com.browser.downloader.videodownloader.data.ProgressInfo;
import com.browser.downloader.videodownloader.data.Video;
import com.browser.downloader.videodownloader.databinding.FragmentProgressBinding;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.ArrayList;

import butterknife.ButterKnife;
import vd.core.util.FileUtil;

public class ProgressFragment extends BaseFragment {

    FragmentProgressBinding mBinding;

    private ProgressAdapter mProgressAdapter;

    private ArrayList<ProgressInfo> mProgressInfos;

    private DownloadManager mDownloadManager;

    public static ProgressFragment getInstance() {
        return new ProgressFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_progress, container, false);
        ButterKnife.bind(this, mBinding.getRoot());
        initUI();

////        // Show ad banner
////        AdUtil.showBanner(this, mBinding.layoutBanner);
//
//        // Load ad interstitial
//        loadInterstitialAd();

        return mBinding.getRoot();
    }

    private void initUI() {
        // Check saved videos
        mDownloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        for (ProgressInfo progressInfo : getProgressInfos()) {
            checkDownloadProgress(progressInfo, mDownloadManager);
        }

        mBinding.rvProgress.setLayoutManager(new LinearLayoutManager(getContext()));
        mProgressAdapter = new ProgressAdapter(getProgressInfos());
        mBinding.rvProgress.setAdapter(mProgressAdapter);
    }

    @Subscribe
    public void onDownloadVideo(Video video) {
        if (!video.isDownloadCompleted()) {
            downloadVideo(video);
        }
    }

    private void downloadVideo(Video video) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(video.getUrl()));

        File localFile = FileUtil.getFolderDir();
        if (!localFile.exists() && !localFile.mkdirs()) return;

        request.setDestinationInExternalPublicDir(FileUtil.FOLDER_NAME, video.getFileName());
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        long downloadId = mDownloadManager.enqueue(request);


        // Save progress info
        ProgressInfo progressInfo = new ProgressInfo();
        progressInfo.setDownloadId(downloadId);
        progressInfo.setVideo(video);
        getProgressInfos().add(progressInfo);
        mProgressAdapter.notifyDataSetChanged();
        mPreferenceManager.setProgress(getProgressInfos());

        // Check progress info
        checkDownloadProgress(progressInfo, mDownloadManager);
    }

    private void checkDownloadProgress(ProgressInfo progressInfo, DownloadManager downloadManager) {

        new Thread(() -> {

            boolean isDownloading = true;

            while (isDownloading) {

                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(progressInfo.getDownloadId());

                Cursor cursor = downloadManager.query(query);
                cursor.moveToFirst();

                if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                    isDownloading = false;
                    mActivity.runOnUiThread(() -> {
                        // Update badges & videos screen
                        progressInfo.getVideo().setDownloadCompleted(true);
                        EventBus.getDefault().post(progressInfo.getVideo());
                        // Update progress screen
                        getProgressInfos().remove(progressInfo);
                        mProgressAdapter.notifyDataSetChanged();
                        mPreferenceManager.setProgress(getProgressInfos());
                    });
                } else if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_FAILED) {
                    isDownloading = false;
                    mActivity.runOnUiThread(() -> {
                        // Update progress screen
                        getProgressInfos().remove(progressInfo);
                        mProgressAdapter.notifyDataSetChanged();
                        mPreferenceManager.setProgress(getProgressInfos());
                    });
                } else if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_RUNNING) {
                    int bytesDownloaded = cursor.getInt(cursor
                            .getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    double dlProgress = (bytesDownloaded * 100f / bytesTotal);

                    mActivity.runOnUiThread(() -> {
                        progressInfo.setProgress((int) dlProgress);
                        progressInfo.setProgressSize(FileUtil.getFileSize(bytesDownloaded) + "/" + FileUtil.getFileSize(bytesTotal));
                        mProgressAdapter.notifyDataSetChanged();
                        mPreferenceManager.setProgress(getProgressInfos());
                    });
                }

                cursor.close();

            }
        }).start();
    }

    private ArrayList<ProgressInfo> getProgressInfos() {
        if (mProgressInfos == null) {
            mProgressInfos = mPreferenceManager.getProgress();
        }
        return mProgressInfos;
    }

}