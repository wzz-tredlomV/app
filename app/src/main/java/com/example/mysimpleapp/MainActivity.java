package com.example.mysimpleapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    
    private EditText inputField;
    private Button submitButton;
    private TextView resultText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化视图组件
        inputField = findViewById(R.id.inputField);
        submitButton = findViewById(R.id.submitButton);
        resultText = findViewById(R.id.resultText);
        
        // 设置按钮点击事件
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = inputField.getText().toString().trim();
                
                if (name.isEmpty()) {
                    Toast.makeText(MainActivity.this, 
                        "请输入您的姓名", Toast.LENGTH_SHORT).show();
                } else {
                    String greeting = "您好, " + name + "! 欢迎使用MySimpleApp!";
                    resultText.setText(greeting);
                }
            }
        });
    }
}
