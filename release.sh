#!/bin/bash
#
# Release script for auto-translate
#
# This script automates the release process:
# 1. Validates prerequisites
# 2. Runs Gradle release (version bump, git commits, tag)
# 3. Pushes to GitHub
# 4. Deploys to Maven Central
# 5. Publishes Gradle plugin
# 6. Creates GitHub release (optional)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Functions
info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

confirm() {
    local prompt="$1"
    local default="${2:-n}"

    if [[ "$default" == "y" ]]; then
        prompt="$prompt [Y/n] "
    else
        prompt="$prompt [y/N] "
    fi

    read -p "$prompt" response
    response=${response:-$default}

    [[ "$response" =~ ^[Yy]$ ]]
}

# Parse arguments
DRY_RUN=false
SKIP_TESTS=false
SKIP_GRADLE_PLUGIN=false
SKIP_GITHUB_RELEASE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-gradle-plugin)
            SKIP_GRADLE_PLUGIN=true
            shift
            ;;
        --skip-github-release)
            SKIP_GITHUB_RELEASE=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --dry-run              Show what would be done without executing"
            echo "  --skip-tests           Skip running tests during Gradle release"
            echo "  --skip-gradle-plugin   Skip publishing Gradle plugin"
            echo "  --skip-github-release  Skip creating GitHub release"
            echo "  -h, --help             Show this help message"
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            ;;
    esac
done

cd "$SCRIPT_DIR"

echo ""
echo "=========================================="
echo "       auto-translate Release Script      "
echo "=========================================="
echo ""

if $DRY_RUN; then
    warn "DRY RUN MODE - No changes will be made"
    echo ""
fi

# Step 0: Validate prerequisites
info "Checking prerequisites..."

# Check we're on master branch
CURRENT_BRANCH=$(git branch --show-current)
if [[ "$CURRENT_BRANCH" != "master" ]]; then
    error "Must be on master branch (currently on: $CURRENT_BRANCH)"
fi
success "On master branch"

# Check for clean working directory
if [[ -n $(git status --porcelain) ]]; then
    echo ""
    git status --short
    echo ""
    error "Working directory is not clean. Please commit or stash changes."
fi
success "Working directory is clean"

# Check Gradle wrapper exists
if [[ ! -x "./gradlew" ]]; then
    error "Gradle wrapper (gradlew) not found or not executable"
fi
success "Gradle wrapper found"

# Check Maven is available
if ! command -v mvn &> /dev/null; then
    error "Maven (mvn) not found in PATH"
fi
success "Maven found"

# Check for GPG
if ! command -v gpg &> /dev/null; then
    warn "GPG not found - Maven deploy may fail"
else
    success "GPG found"
fi

# Check for gh CLI (optional)
HAS_GH_CLI=false
if command -v gh &> /dev/null; then
    HAS_GH_CLI=true
    success "GitHub CLI found"
else
    warn "GitHub CLI (gh) not found - GitHub release will be manual"
fi

# Get current version
CURRENT_VERSION=$(grep "^version=" gradle.properties | cut -d'=' -f2)
info "Current version: $CURRENT_VERSION"

if [[ ! "$CURRENT_VERSION" =~ -SNAPSHOT$ ]]; then
    error "Current version ($CURRENT_VERSION) is not a SNAPSHOT version"
fi

RELEASE_VERSION="${CURRENT_VERSION%-SNAPSHOT}"
info "Release version will be: $RELEASE_VERSION (the Gradle release prompt can override this)"

echo ""
if ! confirm "Proceed with release $RELEASE_VERSION?"; then
    info "Release cancelled"
    exit 0
fi

# Step 1: Run Gradle release
echo ""
echo "=========================================="
echo "Step 1: Gradle Release"
echo "=========================================="
echo ""

GRADLE_ARGS=""
if $SKIP_TESTS; then
    GRADLE_ARGS="-x test"
    warn "Skipping tests"
fi

if $DRY_RUN; then
    info "[DRY RUN] Would run: ./gradlew release $GRADLE_ARGS"

    # Nothing is tagged in a dry run, so fall back to the expected name.
    RELEASE_TAG="$RELEASE_VERSION"
else
    # Remember the tags, so the one created by the release can be identified
    # afterwards.
    TAGS_BEFORE=$(git tag)

    info "Running Gradle release..."
    ./gradlew release $GRADLE_ARGS
    success "Gradle release completed"

    # Take the release tag from what was actually created, never from
    # gradle.properties: The Gradle release plugin asks for the release version
    # interactively, so answering that prompt with anything but the proposed
    # version makes a name derived up front point at a tag that does not exist
    # ("error: src refspec 1.1.5 does not match any").
    #
    # The plugin tags with the bare version (e.g. "1.1.2"), not a "v"-prefixed
    # name - see the existing 1.0.0 / 1.1.0 / 1.1.1 tags.
    NEW_TAGS=$(comm -13 <(printf '%s\n' "$TAGS_BEFORE" | sort) <(git tag | sort))
    NEW_TAG_COUNT=$(printf '%s\n' "$NEW_TAGS" | grep -c . || true)

    if [[ "$NEW_TAG_COUNT" -eq 0 ]]; then
        error "Gradle release created no tag. A tag of the release version may already exist - check 'git tag'."
    elif [[ "$NEW_TAG_COUNT" -gt 1 ]]; then
        error "Gradle release created more than one tag: $(printf '%s' "$NEW_TAGS" | tr '\n' ' ')"
    fi

    RELEASE_TAG="$NEW_TAGS"
    RELEASE_VERSION="$RELEASE_TAG"
    info "Released version: $RELEASE_VERSION (tag $RELEASE_TAG)"
fi

# Step 2: Push to GitHub
echo ""
echo "=========================================="
echo "Step 2: Push to GitHub"
echo "=========================================="
echo ""

if $DRY_RUN; then
    info "[DRY RUN] Would run: git push origin master"
    info "[DRY RUN] Would run: git push origin $RELEASE_TAG"
else
    info "Pushing master branch..."
    git push origin master
    success "Pushed master branch"

    info "Pushing release tag $RELEASE_TAG..."
    git push origin "$RELEASE_TAG"
    success "Pushed release tag"
fi

# Step 3: Deploy to Maven Central
echo ""
echo "=========================================="
echo "Step 3: Deploy to Maven Central"
echo "=========================================="
echo ""

if $DRY_RUN; then
    info "[DRY RUN] Would run: git checkout $RELEASE_TAG"
    info "[DRY RUN] Would run: mvn deploy -Prelease"
    info "[DRY RUN] Would run: git checkout master"
else
    info "Checking out release tag..."
    git checkout "$RELEASE_TAG"

    # Ensure GPG TTY is set
    export GPG_TTY=$(tty)

    info "Deploying to Maven Central..."
    mvn deploy -Prelease
    success "Deployed to Maven Central"

    info "Returning to master branch..."
    git checkout master
fi

# Step 4: Publish Gradle plugin
echo ""
echo "=========================================="
echo "Step 4: Publish Gradle Plugin"
echo "=========================================="
echo ""

if $SKIP_GRADLE_PLUGIN; then
    warn "Skipping Gradle plugin publication (--skip-gradle-plugin)"
elif $DRY_RUN; then
    info "[DRY RUN] Would run: git checkout $RELEASE_TAG"
    info "[DRY RUN] Would run: ./gradlew :auto-translate-gradle-plugin:publishPlugins"
    info "[DRY RUN] Would run: ./gradlew publishToMavenLocal"
    info "[DRY RUN] Would run: git checkout master"
else
    info "Checking out release tag..."
    git checkout "$RELEASE_TAG"

    info "Publishing Gradle plugin..."
    ./gradlew :auto-translate-gradle-plugin:publishPlugins
    success "Published Gradle plugin"

    info "Publishing to Maven local..."
    ./gradlew publishToMavenLocal
    success "Published to Maven local"

    info "Returning to master branch..."
    git checkout master
fi

# Step 5: Create GitHub release
echo ""
echo "=========================================="
echo "Step 5: GitHub Release"
echo "=========================================="
echo ""

if $SKIP_GITHUB_RELEASE; then
    warn "Skipping GitHub release (--skip-github-release)"
elif ! $HAS_GH_CLI; then
    warn "GitHub CLI not available"
    echo ""
    info "Please create the release manually:"
    info "  https://github.com/haumacher/auto-translate/releases/new"
    info "  Tag: $RELEASE_TAG"
elif $DRY_RUN; then
    info "[DRY RUN] Would create GitHub release for $RELEASE_TAG"
else
    if confirm "Create GitHub release for $RELEASE_TAG?" "y"; then
        info "Creating GitHub release..."

        # Generate release notes from recent commits
        RELEASE_NOTES=$(git log --oneline "$(git describe --tags --abbrev=0 "$RELEASE_TAG^")".."$RELEASE_TAG" --pretty=format:"- %s" 2>/dev/null || echo "Release $RELEASE_VERSION")

        gh release create "$RELEASE_TAG" \
            --title "Release $RELEASE_VERSION" \
            --notes "$RELEASE_NOTES"

        success "GitHub release created"
    else
        info "Skipping GitHub release"
        info "Create manually at: https://github.com/haumacher/auto-translate/releases/new"
    fi
fi

# Summary
echo ""
echo "=========================================="
echo "Release Complete!"
echo "=========================================="
echo ""
success "Released version $RELEASE_VERSION"
echo ""
info "Verify artifacts at:"
echo "  - Maven Central (core):   https://search.maven.org/artifact/de.haumacher/auto-translate"
echo "  - Maven Central (plugin): https://search.maven.org/artifact/de.haumacher/auto-translate-maven-plugin"
echo "  - Gradle Plugin Portal:   https://plugins.gradle.org/plugin/de.haumacher.auto-translate-arb"
echo ""
warn "Note: Maven Central artifacts may take some time to appear in search"
