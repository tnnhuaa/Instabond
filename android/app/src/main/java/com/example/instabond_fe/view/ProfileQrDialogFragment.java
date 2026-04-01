package com.example.instabond_fe.view;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.example.instabond_fe.R;
import com.example.instabond_fe.utils.AvatarLoader;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ProfileQrDialogFragment extends DialogFragment {

    private static final String ARG_USER_ID = "user_id";
    private static final String ARG_USERNAME = "username";
    private static final String ARG_AVATAR_URL = "avatar_url";

    public static ProfileQrDialogFragment newInstance(String userId, String username, String avatarUrl) {
        ProfileQrDialogFragment fragment = new ProfileQrDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        args.putString(ARG_USERNAME, username);
        args.putString(ARG_AVATAR_URL, avatarUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Xóa tiêu đề mặc định của Dialog
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.activity_profile_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView ivQrCode = view.findViewById(R.id.iv_qr_code);
        ImageView ivAvatar = view.findViewById(R.id.iv_avatar_qr);
        TextView tvUsername = view.findViewById(R.id.tv_username_qr);
        ImageButton btnBack = view.findViewById(R.id.btn_back_qr);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> dismiss());
        }

        if (getArguments() != null) {
            String userId = getArguments().getString(ARG_USER_ID);
            String username = getArguments().getString(ARG_USERNAME);
            String avatarUrl = getArguments().getString(ARG_AVATAR_URL);

            tvUsername.setText(username != null ? username : "");
            AvatarLoader.load(ivAvatar, avatarUrl);

            if (userId != null) {
                generateQrCode("instabond://user/" + userId, ivQrCode);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Cấu hình kích thước Dialog nổi lên giữa màn hình
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void generateQrCode(String content, ImageView imageView) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 600, 600);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            imageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }
}
