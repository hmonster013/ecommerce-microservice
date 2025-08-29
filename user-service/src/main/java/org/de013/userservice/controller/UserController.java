package org.de013.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResponse;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/legacy" + ApiPaths.USERS)
@RequiredArgsConstructor
@Tag(name = "User Legacy", description = "Legacy user endpoints (deprecated)")
public class UserController extends BaseController {

    private final UserService userService;

    @GetMapping(ApiPaths.ID_PARAM)
    @Operation(summary = "Get user by ID", description = "Retrieve user information by user ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById (
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {

        UserResponse user = userService.getUserById(id);
        return ok(user);
    }

    @GetMapping(ApiPaths.USERNAME_PARAM)
    @Operation(summary = "Get user by username", description = "Retrieve user information by username")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(
            @Parameter(description = "Username", required = true)
            @PathVariable String username) {

        UserResponse user = userService.getUserByUsername(username);
        return ok(user);
    }
}
