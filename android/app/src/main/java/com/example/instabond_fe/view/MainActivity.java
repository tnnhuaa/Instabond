package com.example.instabond_fe.view;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.example.instabond_fe.R;
import com.example.instabond_fe.network.ApiService;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Cấu hình Retrofit trỏ tới máy ảo
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://10.0.2.2:8080/") // IP ma thuật cho máy ảo
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // 2. Tạo service
        ApiService apiService = retrofit.create(ApiService.class);

        // 3. Gọi API Test
        apiService.testConnection().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String result = response.body().string();
                        Log.d("TEST_API", "Thành công: " + result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e("TEST_API", "Lỗi Server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("TEST_API", "Tạch kết nối: " + t.getMessage());
            }
        });
    }
}