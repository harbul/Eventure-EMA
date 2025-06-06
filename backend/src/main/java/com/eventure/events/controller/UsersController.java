package com.eventure.events.controller;


import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.eventure.events.Services.UserService;
import com.eventure.events.model.Users;
import com.eventure.events.repository.UserRepo;

@RestController
@RequestMapping(value = "/api/user")
public class UsersController {

    @Autowired
    UserRepo userRepo;

    @Autowired
    private UserService userService;

    @GetMapping
    public List<Users> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Users> getUserById(@PathVariable String id) {
        Optional<Users> user = userService.getUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

	@PutMapping("/{id}")
	public ResponseEntity<Users> updateUser(@PathVariable String id, @RequestBody Users user) {
		Users updatedUser = userService.updateUser(id, user);
		return updatedUser != null ? ResponseEntity.ok(updatedUser) : ResponseEntity.notFound().build();
	}
/*
	@PostMapping
	public ResponseEntity<Users> createUser(@RequestBody Users user) {
		Users createUser = userService.addNewUser(user);
		return new ResponseEntity<>(createUser, HttpStatus.CREATED);
	}*/

	

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable String id) {
		userService.deleteUser(id);
		return ResponseEntity.noContent().build();
	}

}
