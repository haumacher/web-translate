#!/bin/bash
# Quick Release - One-Click Release Script
# This uses the current version and auto-increments patch version

set -e

# Get current version
current_version=$(grep "^version = " auto-translate/build.gradle | sed "s/version = '\(.*\)'/\1/")

# Remove -SNAPSHOT if present
release_version=${current_version%-SNAPSHOT}

# Auto-increment patch version for next snapshot
if [[ $release_version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    patch="${BASH_REMATCH[3]}"
    next_patch=$((patch + 1))
    next_version="$major.$minor.$next_patch-SNAPSHOT"
else
    echo "ERROR: Invalid version format: $release_version"
    exit 1
fi

echo "🚀 Quick Release"
echo ""
echo "Current version:  $current_version"
echo "Release version:  $release_version"
echo "Next version:     $next_version"
echo ""
echo "This will:"
echo "  ✓ Run all tests"
echo "  ✓ Update all version numbers"
echo "  ✓ Create git tag v$release_version"
echo "  ✓ Prepare for next development iteration"
echo ""
read -p "Continue? [y/N] " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Release cancelled."
    exit 0
fi

./release.sh "$release_version" "$next_version"
