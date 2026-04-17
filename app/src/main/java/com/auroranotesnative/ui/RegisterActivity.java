package com.auroranotesnative.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.databinding.ActivityRegisterBinding;

public class RegisterActivity extends AppCompatActivity {
    public static final String AUTH_PREFS = "auth_prefs";
    public static final String KEY_IS_SIGNED_IN = "is_signed_in";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    private static final int MIN_PASSWORD_LENGTH = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRegisterBinding binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnCreateAccount.setOnClickListener(v -> {
            String email = binding.etEmail.getText() == null ? "" : binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString().trim();
            String confirm = binding.etConfirmPassword.getText() == null
                    ? ""
                    : binding.etConfirmPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirm)) {
                Toast.makeText(this, R.string.login_error_empty_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, R.string.login_error_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < MIN_PASSWORD_LENGTH) {
                Toast.makeText(this, R.string.login_error_short_password, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, R.string.register_error_password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_EMAIL, email)
                    .putString(KEY_PASSWORD, password)
                    .putBoolean(KEY_IS_SIGNED_IN, true)
                    .apply();

            Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, NotesActivity.class));
            finish();
        });

        binding.btnBackToLogin.setOnClickListener(v -> finish());
    }
}
