package com.auroranotesnative.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {
    private static final String AUTH_PREFS = RegisterActivity.AUTH_PREFS;
    private static final String KEY_IS_SIGNED_IN = RegisterActivity.KEY_IS_SIGNED_IN;
    private static final String KEY_EMAIL = RegisterActivity.KEY_EMAIL;
    private static final String KEY_PASSWORD = RegisterActivity.KEY_PASSWORD;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_IS_SIGNED_IN, false)) {
            goToNotes();
            return;
        }

        String savedEmail = prefs.getString(KEY_EMAIL, "");
        binding.etEmail.setText(savedEmail);
        binding.btnGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        binding.btnSignIn.setOnClickListener(v -> {
            String email = binding.etEmail.getText() == null ? "" : binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText() == null ? "" : binding.etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, getString(R.string.login_error_empty_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, getString(R.string.login_error_invalid_email), Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < MIN_PASSWORD_LENGTH) {
                Toast.makeText(this, getString(R.string.login_error_short_password), Toast.LENGTH_SHORT).show();
                return;
            }

            String registeredEmail = prefs.getString(KEY_EMAIL, "");
            String registeredPassword = prefs.getString(KEY_PASSWORD, "");
            if (TextUtils.isEmpty(registeredEmail) || TextUtils.isEmpty(registeredPassword)) {
                Toast.makeText(this, R.string.login_need_register_first, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!registeredEmail.equalsIgnoreCase(email) || !registeredPassword.equals(password)) {
                Toast.makeText(this, R.string.login_error_invalid_credentials, Toast.LENGTH_SHORT).show();
                return;
            }

            boolean rememberMe = binding.cbRememberMe.isChecked();
            prefs.edit()
                    .putBoolean(KEY_IS_SIGNED_IN, rememberMe)
                    .putString(KEY_EMAIL, email)
                    .apply();

            goToNotes();
        });
    }

    private void goToNotes() {
        startActivity(new Intent(this, NotesActivity.class));
        finish();
    }
}
