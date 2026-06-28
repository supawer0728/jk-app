# Project Plan

An Android app named "jkapp" for a couple to manage their life together. Features include Google Drive as a database (JSON schema), Cat care records, and Asset management. The app must use Material Design 3, have a vibrant energy, and support edge-to-edge display.

## Project Brief

# Project Brief: jkapp

A modern, high-energy Android application designed for couples to synchronize their lives, focusing on shared responsibilities and financial transparency.

## Features

*   **Google Drive Cloud Database**: Replaces traditional backends by using Google Drive/Docs to store and sync shared data via a standardized JSON schema.
*   **Cat Care Journal (육묘 기록)**: A dedicated space to track feeding schedules, health records, and milestones for the couple's cats.
*   **Shared Asset Management (자산 관리)**: A collaborative ledger for tracking joint assets, savings goals, and household expenses.
*   **Vibrant Edge-to-Edge Dashboard**: A high-energy home screen providing an immediate overview of cat status and financial health, utilizing the full display area.

## High-Level Technical Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose with **Material Design 3**
*   **Navigation**: **Jetpack Navigation 3** (State-driven architecture)
*   **Adaptive Layouts**: **Compose Material Adaptive** library to ensure a seamless experience across phones, foldables, and tablets.
*   **Concurrency**: Kotlin Coroutines & Flow for reactive cloud data updates.
*   **Networking & Serialization**: Retrofit/OkHttp for Google Drive API integration and **Kotlinx Serialization** for JSON schema enforcement.
*   **Display**: Full Edge-to-Edge implementation using `enableEdgeToEdge`.

## Implementation Steps

### Task_1_Infrastructure_and_Drive_API: Set up the project infrastructure including Google Drive API integration for JSON-based data storage, Kotlinx Serialization models for shared data, and initial Edge-to-Edge configuration.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Google Drive API authentication and sync logic implemented
  - Data models for Cat Care and Assets defined using Kotlinx Serialization
  - enableEdgeToEdge() configured in MainActivity
  - Project builds successfully
- **StartTime:** 2026-06-27 20:40:54 KST

### Task_2_Feature_Modules: Implement the core feature modules: Cat Care Journal (tracking feeding and health) and Shared Asset Management (ledger and savings goals) with their respective ViewModels and UIs.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Cat Care Journal screens implemented with CRUD functionality
  - Shared Asset Management ledger and goal tracking implemented
  - Material Design 3 components used throughout
  - Data correctly persists to/from the Drive repository

### Task_3_Dashboard_and_Navigation: Build the vibrant, high-energy Dashboard and integrate Jetpack Navigation 3 with Compose Material Adaptive for responsive layout support across devices.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Main Dashboard displays summaries of cat status and financial health
  - Navigation 3 correctly handles transitions between modules
  - Adaptive layouts work on different screen sizes (phones, foldables)

### Task_4_Polish_and_Verify: Refine the Material 3 theme with a vibrant color scheme, create an adaptive app icon, and perform final verification.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Vibrant Material 3 color scheme applied (Light/Dark)
  - Adaptive app icon matching the app's theme is created
  - App is stable with no crashes during navigation or data sync
  - Final build passes and requirements are met

