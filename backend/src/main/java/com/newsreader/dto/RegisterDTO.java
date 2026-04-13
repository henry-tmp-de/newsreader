package com.newsreader.dto;

import lombok.Data;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度需在6-30之间")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    /** BEGINNER / INTERMEDIATE / ADVANCED */
    private String level = "BEGINNER";

    /** 兴趣标签，逗号分隔，如 technology,science,sports */
    private String interests;
}
