#!/bin/bash

# Script to push the repository to GitHub
# Repository: https://github.com/aarelaponin/govstack-processing-server.git

echo "========================================"
echo "Pushing to GitHub Repository"
echo "========================================"
echo ""
echo "Target: https://github.com/aarelaponin/govstack-processing-server.git"
echo ""

# Add the remote origin
echo "Adding remote origin..."
git remote add origin https://github.com/aarelaponin/govstack-processing-server.git

# Set the branch name to main
echo "Setting branch to main..."
git branch -M main

# Push to GitHub
echo ""
echo "Pushing to GitHub..."
echo "You may be prompted for your GitHub credentials."
echo ""
git push -u origin main

echo ""
echo "========================================"
echo "Push complete!"
echo "========================================"
echo ""
echo "Your repository is now available at:"
echo "https://github.com/aarelaponin/govstack-processing-server"
echo ""
echo "Next steps:"
echo "1. Visit the repository URL to verify the upload"
echo "2. Add a description and topics on GitHub"
echo "3. Set up GitHub Actions for CI/CD if needed"
echo "4. Configure branch protection rules if desired"