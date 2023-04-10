package io.uslugi.streamer;

import static io.uslugi.streamer.helper.Constants.Config.Advanced.KEEP_STREAMING_WHEN_NOT_IN_FOCUS;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import io.uslugi.streamer.settingsutils.AudioSettings;

public final class LaunchActivity extends AppCompatActivity {

    private AlertDialog mAlert;

    private ActivityResultLauncher<String> mCameraResultLauncher;
    private ActivityResultLauncher<String> mAudioResultLauncher;
    private ActivityResultLauncher<String> mNotificationPermissionResultLauncher;

    private Intent getLaunchIntent() {
        return new Intent(this, LarixActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.hide();
        }

        mCameraResultLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchWithPermissionCheck();
                    } else {
                        onDenied(Manifest.permission.CAMERA);
                    }
                });

        mAudioResultLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchWithPermissionCheck();
                    } else {
                        onDenied(Manifest.permission.RECORD_AUDIO);
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mNotificationPermissionResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> launch());
        }

        // Setup screen preventing dim and sleep
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();

        //Log.v(TAG, "onStart()");

        launchWithPermissionCheck();
    }

    private void launchWithPermissionCheck() {
        final boolean camera = AudioSettings.radioMode() || hasPermission(Manifest.permission.CAMERA);
        final boolean mic = hasPermission(Manifest.permission.RECORD_AUDIO);
        if (camera && mic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                final String postNotifications = Manifest.permission.POST_NOTIFICATIONS;
                if (KEEP_STREAMING_WHEN_NOT_IN_FOCUS && !hasPermission(postNotifications)) {
                    mNotificationPermissionResultLauncher.launch(postNotifications);
                    return;
                }
            }
            launch();
            return;
        }
        if (!camera) {
            if (mCameraResultLauncher != null)
                mCameraResultLauncher.launch(Manifest.permission.CAMERA);
        } else {
            if (mAudioResultLauncher != null)
                mAudioResultLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Log.v(TAG, "onStop()");
        dismissDialog();
    }

    private void launch() {
        final Intent intent = getLaunchIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void onDenied(String permission) {
        if (!shouldShowRequestPermissionRationale(permission)) {
            onNeverAskAgain();
        } else {
            onDenied();
        }
    }

    private void onDenied() {
        showDialog(R.string.permissions_try_again, (dialog, which) -> launchWithPermissionCheck());
    }

    private void onNeverAskAgain() {
        showDialog(R.string.permissions_goto_settings, (dialog, which) -> {
            final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });
    }

    private void showDialog(@StringRes int textId, DialogInterface.OnClickListener clickListener) {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_denied)
                .setMessage(R.string.permissions_denied_hint)
                .setNegativeButton(R.string.permissions_quit, (dialog, which) -> finish())
                .setPositiveButton(textId, clickListener)
                .setCancelable(false);
        showDialog(alert);
    }

    private void showDialog(AlertDialog.Builder dialog) {
        if (!isFinishing()) {
            dismissDialog();
            mAlert = dialog.show();
        }
    }

    private void dismissDialog() {
        if (mAlert != null && mAlert.isShowing()) {
            mAlert.dismiss();
        }
    }
}
