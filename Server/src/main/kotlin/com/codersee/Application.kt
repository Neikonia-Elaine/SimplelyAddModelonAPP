package com.codersee

import com.codersee.plugins.configureSecurity
import com.codersee.plugins.configureSerialization
import com.codersee.repository.NotesRepository
import com.codersee.repository.PhotoRepository
import com.codersee.repository.UserRepository
import com.codersee.routing.configureRouting
import com.codersee.routing.uploadDir
import com.codersee.service.JwtService
import com.codersee.service.UserService
import io.ktor.server.application.*

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  val userRepository = UserRepository()
  val userService = UserService(userRepository)
  val jwtService = JwtService(this, userService)

  val ldapUrl = System.getenv("LDAP_URL") ?: "ldap://ldap:389"
  val mlBase  = System.getenv("ML_BASE_URL") ?: "http://ml:9000"

  val notesRepository = NotesRepository()
  val photoRepository = PhotoRepository(uploadDir)

  configureSerialization()
  configureSecurity(jwtService)
  configureRouting(jwtService, userService, notesRepository, photoRepository, ldapUrl, mlBase)
}