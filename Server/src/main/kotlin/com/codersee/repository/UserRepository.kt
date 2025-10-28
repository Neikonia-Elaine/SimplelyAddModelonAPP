package com.codersee.repository

import com.codersee.model.User
import com.codersee.routing.request.LoginRequest
import com.codersee.routing.request.UserRequest
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.ldap.ldapAuthenticate
import java.util.*
import javax.naming.Context
import javax.naming.directory.BasicAttribute
import javax.naming.directory.BasicAttributes
import javax.naming.directory.InitialDirContext
import javax.naming.ldap.LdapName

class UserRepository {

    private val ldapURL = "ldap://ldap.asd.msd.localhost:389"

    private val baseDN = "dc=ldap,dc=asd,dc=msd,dc=localhost"
    private val adminDN = "cn=admin,$baseDN"
    private val adminPassword = "shouldntMatter"

    fun findByUsername(username: String): User? {
        println("userRepo.findByUsername: $username")

        return try {
            val dc = InitialDirContext(Hashtable<String, Any?>().apply {
                this[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
                this[Context.PROVIDER_URL] = ldapURL
                this[Context.SECURITY_CREDENTIALS] = adminPassword
                this[Context.SECURITY_PRINCIPAL] = adminDN
            })

            val answer = dc.search(
                baseDN,
                BasicAttributes(true).apply {
                    put(BasicAttribute("cn", username))
                },
                arrayOf("cn")
            )

            println("answer: $answer")
            val answerList = answer.toList()
            println("LDAP answerList: $answerList")

            dc.close()

            if (answerList.size == 1)
                User(answerList.first().attributes["cn"]?.get()?.toString() ?: username)
            else
                null
        } catch (ex: Exception) {
            println("Find user failed: ${ex.message}")
            null
        }
    }

    fun save(user: UserRequest): Boolean {
        return try {
            val env = Hashtable<String?, Any?>()
            env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
            env[Context.PROVIDER_URL] = ldapURL
            env[Context.SECURITY_CREDENTIALS] = adminPassword
            env[Context.SECURITY_PRINCIPAL] = adminDN

            val dirContext = InitialDirContext(env)

            val cn = user.username
            val userDN = LdapName("cn=$cn,$baseDN")
            val attributes = BasicAttributes(true).apply {
                put(BasicAttribute("cn", cn))
                put(BasicAttribute("sn", "lastNameIsUnusedButRequired"))
                put("userPassword", user.password)
                put(BasicAttribute("objectClass").apply {
                    add("inetOrgPerson")
                })
            }

            dirContext.createSubcontext(userDN, attributes)
            dirContext.close()

            println("User $cn created successfully in LDAP")
            true

        } catch (ex: Exception) {
            println("Create user failed: ${ex.message}")
            println("stack trace: ${ex.stackTraceToString()}")
            false
        }
    }

    private fun nameToDN(name: String) = LdapName("cn=$name,$baseDN")

    fun ldapAuth(loginRequest: LoginRequest): UserIdPrincipal? {
        return try {
            val pwdCred = UserPasswordCredential(loginRequest.username, loginRequest.password)
            ldapAuthenticate(
                pwdCred,
                ldapURL,
                nameToDN(loginRequest.username).toString()
            )
        } catch (ex: Exception) {
            println("LDAP auth failed: ${ex.message}")
            null
        }
    }
}