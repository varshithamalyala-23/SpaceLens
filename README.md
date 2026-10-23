# SpaceLens — Visual Storage Intelligence System

A Java Swing desktop application that helps users understand disk usage, identify large files, detect duplicates, and review temporary files that may be suitable for cleanup.

## Overview

SpaceLens provides a graphical interface for exploring storage and finding opportunities to free up disk space. It brings storage analysis, duplicate detection, and cleanup recommendations together in one desktop application.

## Features

- **Drive Overview:** View available drives and their storage information.
- **Largest Files Finder:** Scan accessible locations to identify large files.
- **Duplicate File Detection:** Select a folder and identify duplicate files using file sizes and SHA-256 hashing.
- **Storage Insights:** View storage-related statistics, duplicate groups, and potential space savings.
- **Smart Cleanup:** Review older temporary files and receive recommendations before deciding whether to delete them.
- **Background Scanning:** Run scanning tasks in the background, with cancellation support where available.
- **Safety-focused Cleanup:** Review eligible files and confirm before deletion.

## Technologies Used

- **Language:** Java
- **GUI:** Java Swing
- **File Handling:** Java I/O and NIO
- **Data Structures:** Java Collections Framework
- **Hashing:** SHA-256
- **Concurrency:** SwingWorker and background tasks
- **IDE:** Visual Studio Code
- **Version Control:** Git and GitHub

## Requirements

- Java Development Kit (JDK 8 or later)
- Windows, macOS, or Linux with a supported Java environment

## How to Run

### 1. Clone the repository

```bash
git clone https://github.com/varshithamalyala-23/SpaceLens.git
```

### 2. Open the project folder

```bash
cd SpaceLens
```

### 3. Compile the application

```bash
javac SpaceLens.java
```

### 4. Run SpaceLens

```bash
java SpaceLens
```

**Note:** Scanning results depend on folder permissions and the files accessible to the application. Review cleanup recommendations carefully before deleting files.

## Screenshots

Screenshots of the running application will be added here to demonstrate the interface and its main features.

Suggested screenshots:
- Main dashboard and Drive Overview
- Largest Files Finder results
- Duplicate File Detection results
- Storage Insights
- Smart Cleanup recommendations

Save screenshots in a `screenshots/` folder and add them here. For example:

```markdown
![SpaceLens Dashboard](screenshots/spacelens-dashboard.png)
```

## Project Goals

- Make storage information easier to understand.
- Help users identify large and duplicate files.
- Provide useful insights into potential storage savings.
- Encourage careful, informed cleanup decisions.

## Future Improvements

- Storage usage visualizations and charts
- Improved file categorization
- Exportable scan reports
- Additional storage analysis options

## Author``

**Varshitha Malyala**

GitHub: [@varshithamalyala-23](https://github.com/varshithamalyala-23)

---

If you find this project useful, feel free to explore the code and share feedback.
