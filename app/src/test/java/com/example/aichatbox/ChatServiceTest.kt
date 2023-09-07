package com.example.aichatbox

import com.example.aichatbox.model.ChatService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatServiceTest {
    private val chatService = ChatService()

    @Test
    fun testNoIntent() {
        val expectedResponse = "Sorry, I can't help you with that."
        assertEquals(expectedResponse, chatService.getResponse("Good morning!"))
        assertEquals(expectedResponse, chatService.getResponse("Why am I?"))
    }

    @Test
    fun testHelloIntent() {
        val expectedResponse = "Hello there!"
        assertEquals(expectedResponse, chatService.getResponse("hi"))
        assertEquals(expectedResponse, chatService.getResponse("hello"))
        assertEquals(expectedResponse, chatService.getResponse("Hi!"))
        assertEquals(expectedResponse, chatService.getResponse("Hello there!"))
    }

    @Test
    fun testNameIntent() {
        val expectedResponse = "It's a pleasure to meet you!"
        assertEquals(expectedResponse, chatService.getResponse("My name is Bob!"))
    }

    @Test
    fun testDirectionsIntent() {
        assertTrue(chatService.getResponse("I want to get to the robot dog.").contains("robot dog"))
        assertTrue(chatService.getResponse("Directions to the quantum computer?").contains("quantum computer"))
        assertTrue(chatService.getResponse("Go to the toilets.").contains("toilets"))
    }

}