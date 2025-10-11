package com.busuu.app.services.user;


import com.busuu.app.components.JwtTokenUtil;
import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.requests.user.UserActionPasswordDTO;
import com.busuu.app.dtos.requests.user.UserDTO;
import com.busuu.app.dtos.requests.user.UserLoginDTO;
import com.busuu.app.dtos.requests.user.UserUpdateDTO;
import com.busuu.app.dtos.responses.CloudinaryResponse;
import com.busuu.app.dtos.responses.UserInfoResponse;
import com.busuu.app.dtos.responses.UserResponse;
import com.busuu.app.entities.Role;
import com.busuu.app.entities.Token;
import com.busuu.app.entities.User;
import com.busuu.app.entities.enums.PresenceStatus;
import com.busuu.app.exceptions.DataNotFoundException;
import com.busuu.app.exceptions.ErrorHandleException;
import com.busuu.app.exceptions.PermissionDenyException;
import com.busuu.app.repositories.RoleRepository;
import com.busuu.app.repositories.TokenRepository;
import com.busuu.app.repositories.UserRepository;
import com.busuu.app.services.cloudinary.IUploadCloudinaryService;
import com.busuu.app.services.email.IEmailService;
import com.busuu.app.specification.UserSpecification;
import com.busuu.app.utils.LocalizationUtils;
import com.busuu.app.utils.MessagesKey;
import com.busuu.app.utils.UploadCloudinaryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements IUserService {

    // Repositories
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final TokenRepository tokenRepository;

    // Mapper
    private final ModelMapper modelMapper;

    // Services
    private final IEmailService emailService;
    private final IUploadCloudinaryService uploadCloudinaryService;
    private final UserPresenceService userPresenceService;

    // Utils
    private final LocalizationUtils localizationUtils;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(String requestId, UserDTO userDTO) throws Exception {
        try {
            // Check email
            if (userRepository.existsByEmail(userDTO.getEmail())) {
                throw new DataIntegrityViolationException("Email already exists");
            }
            List<Role> roles = userDTO.getRoleIds().stream()
                    .map(roleId -> {
                        try {
                            Role role = roleRepository.findById(roleId)
                                    .orElseThrow(() -> new DataNotFoundException("Cannot find Role with ID = " + roleId));
                            if (role.getName().equalsIgnoreCase("ADMIN")) {
                                throw new PermissionDenyException("You can not register a admin account");
                            }
                            return role;
                        } catch (DataNotFoundException | PermissionDenyException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            User newUser = modelMapper.map(userDTO, User.class);
            newUser.setId(UUID.randomUUID().toString());

            String password = userDTO.getPassword();
            String encodedPassword = passwordEncoder.encode(password);
            newUser.setPassword(encodedPassword);
            newUser.setFullName(newUser.getFirstName() + " " + newUser.getLastName());
            newUser.setLanguageCode("en");
            newUser.setRoles(roles);

            // Active code to active account
            newUser.setActiveCode(UUID.randomUUID().toString());
            newUser.setActive(false);

            // Save new user to db
            userRepository.save(newUser);

            // Send email the active account here!
            emailService.sendEmailActive(newUser.getEmail(), newUser.getActiveCode());
            return modelMapper.map(newUser, UserResponse.class);
        } catch (Exception e) {
            log.error("requestId={},failed to register user, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_REGISTER_USER, requestId);
        }
    }

    @Override
    public String login(String requestId, String email, String password) throws Exception {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new DataNotFoundException("Invalid email or password");
        }
        User existingUser = optionalUser.get();

        // Check password
        if (!passwordEncoder.matches(password, existingUser.getPassword())) {
            throw new BadCredentialsException("Wrong email or password");
        }

        // Authenticate with Java Spring Security
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                email, password,
                existingUser.getAuthorities()
        );
        authenticationManager.authenticate(authenticationToken);
        return jwtTokenUtil.generateToken(existingUser);
    }


    @Override
    public boolean emailUnique(String requestId, String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public User getUserDetailsFromToken(String requestId, String token) throws Exception {
        try {
            if (jwtTokenUtil.isTokenExpired(token)) {
                throw new Exception("Token is expired");
            }
            String email = jwtTokenUtil.extractEmail(token);
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                return user.get();
            } else {
                throw new Exception("User not found");
            }
        } catch (Exception e) {
            log.error("requestId={},failed to get detail user from token, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_USER, requestId);
        }
    }

    @Override
    public User getUserDetailsFromRefreshToken(String requestId, String refreshToken) throws Exception {
        try {
            Token existingToken = tokenRepository.findByRefreshToken(refreshToken);
            return existingToken.getUser();
        } catch (Exception e) {
            log.error("requestId={},failed to get detail user from fresh token, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_USER, requestId);
        }
    }

    @Override
    @Transactional
    public UserResponse updateUser(String requestId, UserUpdateDTO userUpdateDTO) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();
            String userId = user.getId();

            User extUser = userRepository.findById(userId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with ID = " + userId));

            String extAvatar = extUser.getAvatar();

            modelMapper.map(userUpdateDTO, extUser);

            if (Objects.nonNull(userUpdateDTO.getAvatar())) {
                boolean isRemove = true;
                if (extUser.getAvatarName() != null) {
                    isRemove = uploadCloudinaryService.removeFile(extUser.getAvatarName());
                }
                if (isRemove) {
                    CloudinaryResponse cloudinaryResponse = uploadAvatar(userUpdateDTO.getAvatar());
                    if (cloudinaryResponse != null) {
                        extUser.setAvatar(cloudinaryResponse.getUrl());
                        extUser.setAvatarName(cloudinaryResponse.getPublicId());
                    }
                }
            } else {
                extUser.setAvatar(extAvatar);
            }
            extUser.setFullName(extUser.getFirstName() + " " + extUser.getLastName());

            extUser = userRepository.save(extUser);

            return modelMapper.map(extUser, UserResponse.class);
        } catch (Exception e) {
            log.error("requestId={},failed to update user, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_UPDATE_USER, requestId);
        }
    }

    @Override
    @Transactional
    public boolean changePassword(String requestId, UserActionPasswordDTO userActionPasswordDTO) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();
            String userId = user.getId();

            User extUser = userRepository.findById(userId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with ID = " + userId));

            if (passwordEncoder.matches(userActionPasswordDTO.getOldPassword(), extUser.getPassword())) {
                if (userActionPasswordDTO.getPassword().equals(userActionPasswordDTO.getRetypePassword())) {
                    String encodedNewPassword = passwordEncoder.encode(userActionPasswordDTO.getPassword());
                    extUser.setPassword(encodedNewPassword);
                    userRepository.save(extUser);
                    List<Token> tokens = tokenRepository.findByUser(extUser);
                    for (Token tokenItem : tokens) {
                        tokenRepository.delete(tokenItem);
                    }
                    emailService.sendEmailChangedPassword(extUser.getEmail());
                    return true;
                } else {
                    return false;
                }
            }
            return false;

        } catch (Exception e) {
            log.error("requestId={},failed to change password, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CHANGE_PASSWORD, requestId);
        }
    }

    @Override
    public Page<UserResponse> getUsersByRole(String requestId, String roleName, int page, int size, List<String> sortBy, List<String> sortDirection, String searchValue, String isActive) {
        try {

            ///Temp comment, uncomment if use custom repository query, delete later if not use
            //Pageable - NativeQuery
//            Sort sort = Sort.by(
//                    Sort.Order.by(sortBy).with(Sort.Direction.fromString(sortDirection)),
//                    Sort.Order.by("user_id").with(Sort.Direction.fromString(sortDirection))
//            );

//            Pageable pageable = PageRequest.of(page, size);

//            return userRepository.findUsersByRoleName(roleName, pageable).map(
//                    user -> modelMapper.map(user, UserResponse.class)
//            );

            Pageable pageable = PageRequest.of(page, size);

            Specification<User> spec = UserSpecification.getSpecification(roleName, searchValue, isActive, sortBy, sortDirection);
            return userRepository.findAll(spec, pageable).map(
                      user -> modelMapper.map(user, UserResponse.class)
            );


        } catch (Exception e) {
            log.error("requestId={},failed to get users by role, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_USER, requestId);
        }
    }

    @Override
    public UserResponse getUserById(String requestId, String userId) {
        try {
            User existingUser = userRepository.findById(userId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with ID = " + userId));

            UserResponse userResponse = modelMapper.map(existingUser, UserResponse.class);
            userResponse.setStatus(userPresenceService.isOnline(requestId, userId) ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE);
            userResponse.setLastSeenAt(existingUser.getLastSeenAt());

            return userResponse;

        } catch (Exception e) {
            log.error("requestId={},failed to get user by id, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_USER, requestId);
        }
    }

    @Override
    @Cacheable(
            value = "user-info-cache",
            key = "'userId:' + #userId"
    )
    public UserInfoResponse getUserInfoById(String requestId, String userId)
    {
        try {
            User existingUser = userRepository.findById(userId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with ID = " + userId));
            UserInfoResponse res = modelMapper.map(existingUser, UserInfoResponse.class);
            res.setStatus(userPresenceService.isOnline(requestId, userId) ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE);
            return res;
        } catch (Exception e) {
            log.error("requestId={},failed to get user by id, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_USER, requestId);
        }
    }

    @Override
    @Transactional
    public int activeAccount(String requestId, String email, String activeCode) throws DataNotFoundException {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with Email = " + email));

            if (user.isActive()) {
                return 1;
            }

            if (activeCode.equals(user.getActiveCode())) {
                user.setActive(true);
                userRepository.save(user);
                return 2;
            }
            return 0;
        } catch (Exception e) {
            log.error("requestId={},failed to active account, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_ACTIVE_ACCOUNT, requestId);
        }
    }


    // Handle -> Use redis
    @Override
    @Transactional
    public int generateOTP(String requestId, String email) throws DataNotFoundException {
        try {
            User existingUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with Email = " + email));
            if (existingUser.getOtp() != null) {
                existingUser.setOtp(null);
                userRepository.save(existingUser);
                return 1;
            } else {
                SecureRandom random = new SecureRandom();
                int otp = 100000 + random.nextInt(900000);
                existingUser.setOtp(String.valueOf(otp));
                emailService.sendEmailOtp(existingUser.getEmail(), String.valueOf(otp));
                userRepository.save(existingUser);
                return 2;
            }
        } catch (Exception e) {
            log.error("requestId={},failed to generate otp, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GENERATE_OTP, requestId);
        }
    }

    @Override
    @Transactional
    public boolean checkOTP(String requestId, String email, String OTP) throws DataNotFoundException {
        try {
            User existingUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with Email = " + email));
            if (existingUser.getOtp().equals(OTP)) {
                existingUser.setOtp(null);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("requestId={},failed to check otp, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CHECK_OTP, requestId);
        }
    }

    @Override
    @Transactional
    public User blockOrEnable(String requestId, String userId) throws DataNotFoundException {
        try {
            User existingUser = userRepository.findById(userId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find User with ID = " + userId));
            existingUser.setActive(!existingUser.isActive());
            return userRepository.save(existingUser);
        } catch (Exception e) {
            log.error("requestId={},failed to block or enable user, err={}", requestId, e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CHECK_OTP, requestId);
        }
    }

    @Override
    @Transactional
    public String loginSocial(UserLoginDTO userLoginDTO) throws Exception {
        Optional<User> optionalUser = Optional.empty();
        Role roleUser = roleRepository.findById("9c6cda15-a8e9-41d1-9e2c-f7e2edda24e9")
                .orElseThrow(() -> new DataNotFoundException(
                        localizationUtils.getLocalizedMessage(MessagesKey.INVALID_ERROR)));
        List<Role> roles = new ArrayList<>();
        roles.add(roleUser);
        if (userLoginDTO.isGoogleAccountIdValid()) {
            optionalUser = userRepository.findByEmail(userLoginDTO.getEmail());

            if (optionalUser.isEmpty()) {
                User newUser = User.builder()
                        .id(UUID.randomUUID().toString())
                        .firstName(userLoginDTO.getFirstName())
                        .email(userLoginDTO.getEmail())
                        .lastName(userLoginDTO.getLastName())
                        .roles(roles)
                        .avatar(userLoginDTO.getAvatar())
                        .googleAccountId(userLoginDTO.getGoogleAccountId())
                        .password("")
                        .isActive(true)
                        .languageCode("en")
                        .build();
                newUser = userRepository.save(newUser);
                optionalUser = Optional.of(newUser);
            }
            else {
                if (optionalUser.get().getGoogleAccountId() == null) {
                    optionalUser.get().setGoogleAccountId(userLoginDTO.getGoogleAccountId());
                    optionalUser = Optional.of(userRepository.save(optionalUser.get()));
                }
            }
        }
        else if (userLoginDTO.isFacebookAccountIdValid()) {
            optionalUser = userRepository.findByEmail(userLoginDTO.getEmail());
            if (optionalUser.isEmpty()) {
                User newUser = User.builder()
                        .id(UUID.randomUUID().toString())
                        .firstName(userLoginDTO.getFirstName())
                        .email(userLoginDTO.getEmail())
                        .lastName(userLoginDTO.getLastName())
                        .roles(roles)
                        .avatar(userLoginDTO.getAvatar())
                        .facebookAccountId(userLoginDTO.getFacebookAccountId())
                        .password("")
                        .isActive(true)
                        .languageCode("en")
                        .build();

                newUser = userRepository.save(newUser);
                optionalUser = Optional.of(newUser);
            }
            else {
                if (optionalUser.get().getFacebookAccountId() == null) {
                    optionalUser.get().setFacebookAccountId(userLoginDTO.getFacebookAccountId());
                    optionalUser = Optional.of(userRepository.save(optionalUser.get()));
                }
            }
        } else {
            throw new IllegalArgumentException("Invalid social account information.");
        }

        User user = optionalUser.get();

        if (!user.isActive()) {
            throw new IllegalArgumentException(localizationUtils.getLocalizedMessage("User is locked"));
        }

        return jwtTokenUtil.generateToken(user);
    }

    private CloudinaryResponse uploadAvatar(MultipartFile file) throws Exception {
        UploadCloudinaryUtil.assertAllowed(file, "image");
        String fileName = UploadCloudinaryUtil.getFileName(file.getOriginalFilename());
        CloudinaryResponse response = uploadCloudinaryService.uploadFile(file, fileName, "user");
        return response;
    }
}
