package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.DTO.AuthRequest;
import com.JanSahayak.AI.DTO.AuthResponse;
import com.JanSahayak.AI.DTO.RegisterRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Incorrect username or password. Please try again."));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No account found with this email address."));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Your account has been disabled. Please contact support."));
        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An internal error occurred. Please try again later."));
        }
    }

    @PostMapping("/register/citizen")
    @Transactional
    public ResponseEntity<ApiResponse<String>> registerUser(@RequestBody RegisterRequest request) {
        try {
            // FIX: Use existsByEmail() instead of findByEmail().isPresent()
            // existsByEmail() runs a COUNT query — much cheaper than loading a full User entity
            // just to check if it exists.
            if (userRepository.existsByEmail(request.getEmail())) {
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
            // FIX: Use existsByEmail() instead of findByEmail().isPresent()
            if (userRepository.existsByEmail(request.getEmail())) {
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
            // FIX: Use existsByEmail() instead of findByEmail().isPresent()
            if (userRepository.existsByEmail(request.getEmail())) {
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

    /**
     * FIX: Bounded username generation loop.
     *
     * The original do-while had no upper limit — if the username space were saturated,
     * it would loop forever burning DB connections. The outer retry guard in
     * registerUser() had a max of 10, but generateUniqueUsername() itself was unbounded.
     *
     * Fixed to try at most 20 combinations before throwing. Given ~150 adjectives ×
     * ~150 nouns × 9000 numbers = ~202,500,000 combinations, 20 attempts will succeed
     * in practice. The exception surfaces the same "try again" 500 that was already
     * shown to the user when the outer 10-retry loop failed.
     */
    private String generateUniqueUsername() {
        String[] adjectives = {
                "Happy","Brave","Swift","Clever","Mighty","Silent","Wise","Lucky","Bold","Shiny",
                "Fierce","Calm","Wild","Bright","Cool","Fast","Gentle","Sharp","Loyal","Kind",
                "Strong","Fearless","Quiet","Sneaky","Cheerful","Noble","Radiant","Epic","Smart","Eager",
                "Playful","Energetic","Glorious","Charming","Courageous","Heroic","Friendly","Polite","Magical","Mystic",
                "Joyful","Adventurous","Brilliant","Daring","Faithful","Generous","Humble","Creative","Graceful","Dynamic",
                "Witty","Keen","Determined","Sunny","Starry","Vivid","Dazzling","Zesty","Glowing","Chill",
                "Funky","Coolheaded","Quick","Resourceful","Inventive","Cheeky","Blissful","Hopeful","Valiant","Luminous",
                "Cosmic","Fiery","Dreamy","Tranquil","Golden","Boldhearted","Eternal","Zen","Spirited","Vast",
                "Skybound","Stellar","Brighthearted","Roaring","Free","Harmonic","Nimble","Gallant","Sturdy","Calmhearted",
                "Swiftfooted","Iron","Steady","Thunderous","Silentblade","Quickwitted","Stormy","Snowy","Frosty","Burning",
                "Shadowy","Crimson","Silver","Serene","Ancient","Wildhearted","Springtime","Moonlit","Sunlit","Windswept"
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
                "Griffin","Hydra","Cerberus","Unicorn","Pegasus","Minotaur","Yeti","Bigfoot","Werewolf","Ghoul",
                "Sphinx","Chimera","Basilisk","Hippogriff","Mermaid","Phoenix","Wyvern","Leviathan","Titan","Golem"
        };

        for (int i = 0; i < 20; i++) {
            String adjective = adjectives[(int) (Math.random() * adjectives.length)];
            String noun      = nouns[(int) (Math.random() * nouns.length)];
            int    number    = (int) (Math.random() * 9000) + 1000;
            String candidate = adjective + noun + number;

            if (candidate.length() > 3 && !userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        // Practically unreachable given 200M+ combinations, but we must be bounded.
        throw new ServiceException("Could not generate a unique username after 20 attempts. Please try again.");
    }
}