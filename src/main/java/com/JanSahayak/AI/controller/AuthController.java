package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.AuthRequest;
import com.JanSahayak.AI.DTO.AuthResponse;
import com.JanSahayak.AI.DTO.RegisterRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.repository.RoleRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.security.JwtUtil;
import com.JanSahayak.AI.security.CustomUserDetailsService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepo userRepository;
    @Autowired private RoleRepo roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            final String jwt = jwtUtil.generateToken(authentication);

            return ResponseEntity.ok(ApiResponse.success("Login successful", new AuthResponse(jwt)));

        } catch (BadCredentialsException e) {
            // This exception is specifically for incorrect passwords.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Incorrect username or password. Please try again."));

        } catch (UsernameNotFoundException e) {
            // This exception is thrown by your UserDetailsService if the email is not found.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No account found with this email address."));

        } catch (DisabledException e) {
            // This can be used if you have a feature to disable user accounts.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Your account has been disabled. Please contact support."));

        } catch (Exception e) {
            // A general fallback for any other unexpected errors during authentication.
            log.error("Authentication failed for user {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An internal error occurred. Please try again later."));
        }
    }

    @PostMapping("/register/citizen")
    @Transactional
    public ResponseEntity<ApiResponse<String>> registerUser(@RequestBody RegisterRequest request) {
        try {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists"));
            }

            User user = new User();
            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            Role CitizenRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(CitizenRole);
            user.setPincode(request.getPincode());

            int maxRetries = 10;
            boolean saved = false;

            for (int attempt = 0; attempt < maxRetries && !saved; attempt++) {
                try {
                    user.setUsername(generateUniqueUsername());
                    userRepository.save(user);
                    saved = true;
                } catch (DataIntegrityViolationException e) {
                    log.debug("Username collision on attempt {}, retrying...", attempt + 1);
                }
            }

            if (!saved) {
                log.error("Failed to generate unique username after {} attempts", maxRetries);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Unable to generate unique username. Please try again."));
            }

            return ResponseEntity.ok(ApiResponse.success("Citizen registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed"));
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/register/department")
    @Transactional
    public ResponseEntity<ApiResponse<String>> registerDepartment(@RequestBody RegisterRequest request) {
        try {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists"));
            }

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username is required"));
            }

            if (userRepository.existsByUsername(request.getUsername().trim())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username already exists"));
            }

            User user = new User();
            user.setEmail(request.getEmail());
            user.setUsername(request.getUsername().trim());
            user.setPassword(passwordEncoder.encode(request.getPassword()));

            Role DepartmentRole = roleRepository.findByName("ROLE_DEPARTMENT")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(DepartmentRole);
            user.setPincode(request.getPincode());

            userRepository.save(user);
            log.info("Department user registered successfully: {}", user.getUsername());

            return ResponseEntity.ok(ApiResponse.success("Government department registered successfully"));

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during department registration: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Registration failed: Username or email already exists"));
        } catch (Exception e) {
            log.error("Error during department registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed due to server error"));
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/register/admin")
    @Transactional
    public ResponseEntity<ApiResponse<String>> registerAdmin(@RequestBody RegisterRequest request) {
        try {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists"));
            }

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username is required"));
            }

            if (userRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username already exists"));
            }

            User user = new User();
            user.setEmail(request.getEmail());
            user.setUsername(request.getUsername().trim());
            user.setPassword(passwordEncoder.encode(request.getPassword()));

            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(adminRole);
            user.setPincode(request.getPincode());

            userRepository.save(user);
            log.info("Admin user registered successfully: {}", user.getUsername());

            return ResponseEntity.ok(ApiResponse.success("ADMIN has registered successfully"));

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during admin registration: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Registration failed: Username or email already exists"));
        } catch (Exception e) {
            log.error("Error during admin registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed due to server error"));
        }
    }

    private String generateUniqueUsername() {
        String[] adjectives = {
                "Happy","Brave","Swift","Clever","Mighty","Silent","Wise","Lucky","Bold","Shiny",
                "Fierce","Calm","Wild","Bright","Cool","Fast","Gentle","Sharp","Loyal","Kind",
                "Strong","Fearless","Quiet","Sneaky","Cheerful","Noble","Radiant","Epic","Smart","Eager",
                "Playful","Energetic","Glorious","Charming","Courageous","Heroic","Friendly","Polite","Magical","Mystic",
                "Joyful","Adventurous","Brilliant","Daring","Faithful","Generous","Humble","Creative","Graceful","Dynamic",
                "Witty","Keen","Determined","Epic","Sunny","Starry","Shiny","Epic","Vivid","Dazzling",
                "Gentle","Zesty","Glowing","Chill","Funky","Epic","Coolheaded","Quick","Resourceful","Inventive",
                "Cheeky","Radiant","Blissful","Epic","Hopeful","Valiant","Luminous","Cosmic","Epic","Fiery",
                "Dreamy","Tranquil","Golden","Epic","Boldhearted","Clever","Fearless","Epic","Eternal","Zen",
                "Spirited","Wildhearted","Vast","Epic","Skybound","Stellar","Brighthearted","Epic","Roaring","Free",
                "Epic","Harmonic","Nimble","Epic","Gallant","Sturdy","Calmhearted","Epic","Swiftfooted","Iron",
                "Epic","Steady","Thunderous","Epic","Silentblade","Epic","Quickwitted","Epic","Stormy","Snowy",
                "Epic","Frosty","Epic","Burning","Epic","Shadowy","Epic","Crimson","Epic","Silver",
                "Epic","Epicurean","Epic","Serene","Epic","Ancient","Epic","Epic"
        };

        String[] nouns = {
                "Tiger","Eagle","Shark","Panther","Wolf","Falcon","Lion","Bear","Hawk","Cheetah",
                "Puma","Dragon","Phoenix","Leopard","Viper","Cobra","Fox","Jaguar","Lynx","Raven",
                "Crow","Owl","Stallion","Mustang","Horse","Buffalo","Bison","Bull","Ram","Goat",
                "Deer","Moose","Elk","Yak","Elephant","Rhino","Hippo","Gorilla","Chimp","Orangutan",
                "Whale","Dolphin","Seal","Otter","Penguin","PolarBear","Camel","Giraffe","Kangaroo","Koala",
                "Crocodile","Alligator","Turtle","Tortoise","Frog","Toad","Eel","Octopus","Squid","Jellyfish",
                "Starfish","Crab","Lobster","Shrimp","Mantis","Scorpion","Spider","Beetle","Wasp","Hornet",
                "Ant","Bee","Butterfly","Moth","Dragonfly","Ladybug","Firefly","Bat","Rat","Mouse",
                "Squirrel","Chipmunk","Porcupine","Hedgehog","Ferret","Badger","Weasel","Armadillo","Sloth","Anteater",
                "Parrot","Macaw","Canary","Sparrow","Robin","Finch","Swallow","Seagull","Pelican","Albatross",
                "Heron","Flamingo","Swan","Duck","Goose","Turkey","Chicken","Rooster","Peacock","Dove",
                "Pigeon","Caterpillar","Worm","Snail","Slug","Clam","Oyster","Mussel","Coral","Barnacle",
                "Jelly","Kraken","Cyclops","Golem","Griffin","Hydra","Cerberus","Unicorn","Pegasus","Minotaur",
                "Yeti","Bigfoot","Sasquatch","Werewolf","Zombie","Ghoul","Vampire","Mummy","Sphinx","Chimera",
                "Basilisk","Hippogriff","Mermaid","Kraken","Phoenix","Dragonlord","Wyvern","SeaSerpent","Leviathan","Titan"
        };

        String username;
        do {
            String adjective = adjectives[(int) (Math.random() * adjectives.length)];
            String noun = nouns[(int) (Math.random() * nouns.length)];
            int number = (int) (Math.random() * 9000) + 1000;

            username = adjective + noun + number;
        } while (username.length() <= 3 || userRepository.existsByUsername(username));

        return username;
    }
}