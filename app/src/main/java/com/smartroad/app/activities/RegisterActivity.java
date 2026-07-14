package com.smartroad.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smartroad.app.R;
import com.smartroad.app.utils.SupabaseClient;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPhone, etPassword;
    private Button btnRegister;
    private TextView tvLoginLink;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        SupabaseClient.init(this);

        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);
        etPassword = findViewById(R.id.et_password);
        btnRegister = findViewById(R.id.btn_register);
        tvLoginLink = findViewById(R.id.tv_login_link);
        progressBar = findViewById(R.id.progress_bar);

        btnRegister.setOnClickListener(v -> registerUser());

        tvLoginLink.setOnClickListener(v ->
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class)));
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        SupabaseClient.signUp(email, password, name, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userName) {
                if (SupabaseClient.isLoggedIn()) {
                    // Session saved from signup (auto-confirm enabled)
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this,
                            "Registration successful!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                } else {
                    // Auto-confirm disabled - need to sign in
                    SupabaseClient.signIn(email, password, new SupabaseClient.AuthCallback() {
                        @Override
                        public void onSuccess(String id, String name) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(RegisterActivity.this,
                                    "Registration successful!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                            finish();
                        }

                        @Override
                        public void onFailure(String error) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(RegisterActivity.this,
                                    "Please check your email to confirm, then log in.",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        }
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RegisterActivity.this,
                        "Registration failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
