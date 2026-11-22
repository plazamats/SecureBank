package za.co.securebank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.securebank.dto.request.UpdateProfileRequest;
import za.co.securebank.dto.response.UserResponse;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get the profile of the authenticated user")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getProfile(principal);
    }

    @PutMapping("/me")
    @Operation(summary = "Update the profile of the authenticated user")
    public UserResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal, request);
    }
}
