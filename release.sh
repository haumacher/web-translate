#!/bin/bash
set -e  # Exit on error

# Auto-Translate Multi-Module Release Script
# This script automates the release process for all modules

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
error() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}

info() {
    echo -e "${YELLOW}➜ $1${NC}"
}

# Check if working directory is clean
check_clean_working_tree() {
    if ! git diff-index --quiet HEAD --; then
        error "Working directory is not clean. Please commit or stash your changes."
    fi
    success "Working directory is clean"
}

# Get current version from build.gradle
get_current_version() {
    grep "^version = " auto-translate/build.gradle | sed "s/version = '\(.*\)'/\1/"
}

# Update version in a Gradle build file
update_gradle_version() {
    local file=$1
    local new_version=$2

    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s/^version = '.*'/version = '$new_version'/" "$file"
    else
        # Linux
        sed -i "s/^version = '.*'/version = '$new_version'/" "$file"
    fi
    success "Updated $file to version $new_version"
}

# Update Maven POM version
update_maven_version() {
    local new_version=$1
    cd auto-translate-maven-plugin
    mvn -q versions:set -DnewVersion="$new_version" -DgenerateBackupPoms=false
    cd ..
    success "Updated Maven plugin POM to version $new_version"
}

# Update version in README examples
update_readme_version() {
    local old_version=$1
    local new_version=$2

    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s/$old_version/$new_version/g" README.md
    else
        # Linux
        sed -i "s/$old_version/$new_version/g" README.md
    fi
    success "Updated README.md version references"
}

# Main release function
perform_release() {
    local release_version=$1
    local next_version=$2

    info "Starting release process for version $release_version"

    # Check prerequisites
    check_clean_working_tree

    # Get current version
    local current_version=$(get_current_version)
    info "Current version: $current_version"
    info "Release version: $release_version"
    info "Next version: $next_version"

    # Run tests
    info "Running tests..."
    ./gradlew clean test || error "Tests failed"
    success "All tests passed"

    # Update to release version
    info "Updating to release version $release_version..."
    update_gradle_version "auto-translate/build.gradle" "$release_version"
    update_gradle_version "auto-translate-gradle-plugin/build.gradle" "$release_version"
    update_maven_version "$release_version"
    update_readme_version "$current_version" "$release_version"

    # Build everything
    info "Building all modules..."
    ./gradlew build || error "Build failed"
    success "Build successful"

    # Commit release version
    info "Committing release version..."
    git add -A
    git commit -m "Release version $release_version"
    git tag -a "v$release_version" -m "Release version $release_version"
    success "Created tag v$release_version"

    # Publish to Maven Local for testing
    info "Publishing to Maven Local..."
    ./gradlew publishToMavenLocal
    success "Published to Maven Local"

    # Update to next development version
    info "Updating to next development version $next_version..."
    update_gradle_version "auto-translate/build.gradle" "$next_version"
    update_gradle_version "auto-translate-gradle-plugin/build.gradle" "$next_version"
    update_maven_version "$next_version"
    update_readme_version "$release_version" "$next_version"

    # Commit next version
    git add -A
    git commit -m "Prepare for next development iteration ($next_version)"
    success "Updated to next development version"

    echo ""
    success "Release $release_version completed successfully!"
    echo ""
    echo "Next steps:"
    echo "  1. Review the changes:"
    echo "     git log --oneline -3"
    echo ""
    echo "  2. Push to GitHub:"
    echo "     git push origin master"
    echo "     git push origin v$release_version"
    echo ""
    echo "  3. Publish Maven plugin to Maven Central:"
    echo "     cd auto-translate-maven-plugin"
    echo "     mvn deploy -Prelease"
    echo ""
    echo "  4. Publish Gradle plugin to Gradle Plugin Portal:"
    echo "     git checkout v$release_version"
    echo "     cd auto-translate-gradle-plugin"
    echo "     ../gradlew publishPlugins"
    echo ""
    echo "  5. Create GitHub release at:"
    echo "     https://github.com/haumacher/auto-translate/releases/new?tag=v$release_version"
}

# Parse command line arguments
if [ $# -lt 1 ]; then
    current_version=$(get_current_version)
    # Remove -SNAPSHOT if present
    suggested_release=${current_version%-SNAPSHOT}

    echo "Usage: $0 <release-version> [next-snapshot-version]"
    echo ""
    echo "Current version: $current_version"
    echo "Suggested release version: $suggested_release"
    echo ""
    echo "Example:"
    echo "  $0 1.0.1 1.0.2-SNAPSHOT"
    echo ""
    exit 1
fi

release_version=$1

# If next version not provided, auto-increment
if [ $# -lt 2 ]; then
    # Extract version numbers
    if [[ $release_version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        major="${BASH_REMATCH[1]}"
        minor="${BASH_REMATCH[2]}"
        patch="${BASH_REMATCH[3]}"
        next_patch=$((patch + 1))
        next_version="$major.$minor.$next_patch-SNAPSHOT"
    else
        error "Invalid version format: $release_version (expected: X.Y.Z)"
    fi
else
    next_version=$2
fi

# Confirm release
echo ""
echo "Release Configuration:"
echo "  Release version: $release_version"
echo "  Next version:    $next_version"
echo ""
read -p "Proceed with release? [y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Release cancelled."
    exit 0
fi

perform_release "$release_version" "$next_version"
