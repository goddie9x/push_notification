package com.god.pushnotification;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.god.pushnotification.R;

public class MainActivity extends AppCompatActivity {
    private EditText notificationTitle;
    private EditText notificationBody;
    private NumberPicker amountNotification;
    private NumberPicker timePushNotificationInterval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initData();
    }

    private void initData() {
        initPushNotificationBtn();
        initAmountNotificationField();
        initTimeIntervalPushNotificationField();
        notificationTitle = findViewById(R.id.notification_title);
        notificationBody = findViewById(R.id.notification_body);
    }

    private void initTimeIntervalPushNotificationField() {
        timePushNotificationInterval = findViewById(R.id.time_push_notification_interval);

        timePushNotificationInterval.setMinValue(1000);
        timePushNotificationInterval.setMaxValue(30000);
        timePushNotificationInterval.setValue(1000);
    }

    private void initAmountNotificationField() {
        amountNotification = findViewById(R.id.amount_notification);

        amountNotification.setMinValue(1);
        amountNotification.setMaxValue(100);
        amountNotification.setValue(1);
    }

    private void initPushNotificationBtn() {
        Button pushNotificationBtn = findViewById(R.id.push_notification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel("default", "Channel Name",
                            NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        pushNotificationBtn.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "You have to allow post notification for our app to push notification",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            int amountNotificationToPush = amountNotification.getValue();
            int timeInterval = timePushNotificationInterval.getValue();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "default")
                    .setSmallIcon(R.drawable.push_notification_ic)
                    .setContentText(notificationBody.getText())
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            for (int i = 0; i < amountNotificationToPush; i++) {
                builder.setContentText(notificationTitle.getText().toString() + i);
                notificationManager.notify(i, builder.build());

                try {
                    Thread.sleep(timeInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}