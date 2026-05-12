#!/bin/bash

# GitHub Setup Script for GovStack Processing Server Plugin
# This script helps you initialize and push the project to GitHub

echo "========================================"
echo "GitHub Setup for Processing Server Plugin"
echo "========================================"

# Step 1: Initialize git repository
echo ""
echo "Step 1: Initializing Git repository..."
git init

# Step 2: Add all files
echo ""
echo "Step 2: Adding files to Git..."
git add .

# Step 3: Create initial commit
echo ""
echo "Step 3: Creating initial commit..."
git commit -m "Initial commit: GovStack Processing Server Plugin v8.1

- Complete field mapping implementation
- Validation framework included
- Comprehensive documentation
- All September 2025 fixes applied"

echo ""
echo "========================================"
echo "NEXT STEPS:"
echo "========================================"
echo ""
echo "1. Create a new repository on GitHub:"
echo "   - Go to https://github.com/new"
echo "   - Name: govstack-processing-server (or your preferred name)"
echo "   - Description: Joget plugin for processing GovStack API data"
echo "   - Keep it public or private as needed"
echo "   - DO NOT initialize with README, .gitignore, or license"
echo ""
echo "2. After creating the repository, run these commands:"
echo ""
echo "   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git"
echo "   git branch -M main"
echo "   git push -u origin main"
echo ""
echo "3. Optional: Create a develop branch for ongoing work:"
echo "   git checkout -b develop"
echo "   git push -u origin develop"
echo ""
echo "========================================"
echo "Repository is ready for GitHub!"
echo "========================================"