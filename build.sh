#!/bin/bash

# ============================================================================
# LogicFlow Build Script
# Prolog-like DSL with STM and Web Visualizer
# ============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo -e "${CYAN}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║           LogicFlow Build System                           ║"
echo "║       Prolog-like DSL with STM & Web Visualizer            ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ============================================================================
# Helper Functions
# ============================================================================

print_step() {
    echo -e "\n${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed"
        return 1
    fi
    return 0
}

# ============================================================================
# Commands
# ============================================================================

cmd_check() {
    print_step "Checking dependencies..."
    
    local all_ok=true
    
    if check_command java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
        print_success "Java: $JAVA_VERSION"
    else
        all_ok=false
    fi
    
    if check_command clj; then
        CLJ_VERSION=$(clj --version 2>&1 | head -n 1)
        print_success "Clojure CLI: $CLJ_VERSION"
    else
        all_ok=false
    fi
    
    if check_command node; then
        NODE_VERSION=$(node --version)
        print_success "Node.js: $NODE_VERSION"
    else
        all_ok=false
    fi
    
    if check_command npm; then
        NPM_VERSION=$(npm --version)
        print_success "npm: $NPM_VERSION"
    else
        all_ok=false
    fi
    
    if $all_ok; then
        print_success "All dependencies are installed!"
    else
        print_error "Some dependencies are missing"
        exit 1
    fi
}

cmd_deps() {
    print_step "Installing dependencies..."
    
    # Install npm dependencies
    if [ -f "package.json" ]; then
        echo "Installing npm packages..."
        npm install
        print_success "npm dependencies installed"
    fi
    
    # Download Clojure dependencies
    echo "Downloading Clojure dependencies..."
    clj -P
    print_success "Clojure dependencies downloaded"
}

cmd_clean() {
    print_step "Cleaning build artifacts..."
    
    rm -rf .shadow-cljs
    rm -rf resources/public/js
    rm -rf target
    rm -rf .cpcache
    rm -rf node_modules/.cache
    
    print_success "Build artifacts cleaned"
}

cmd_build_frontend() {
    print_step "Building frontend (ClojureScript)..."
    
    # Ensure dependencies are installed
    if [ ! -d "node_modules" ]; then
        npm install
    fi
    
    # Build production JS
    npx shadow-cljs release app
    
    print_success "Frontend built: resources/public/js/main.js"
}

cmd_build_backend() {
    print_step "Building backend (Clojure)..."
    
    # AOT compile (optional, for faster startup)
    # clj -T:build uber
    
    print_success "Backend ready (interpreted mode)"
}

cmd_build() {
    cmd_clean
    cmd_deps
    cmd_build_frontend
    cmd_build_backend
    
    echo -e "\n${GREEN}╔════════════════════════════════════════╗"
    echo "║        Build Complete!                 ║"
    echo "╚════════════════════════════════════════╝${NC}"
}

cmd_dev() {
    print_step "Starting development environment..."
    
    # Ensure dependencies are installed
    if [ ! -d "node_modules" ]; then
        print_warning "node_modules not found, installing..."
        npm install
    fi
    
    # Kill any existing processes on our ports
    print_step "Cleaning up existing processes..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    lsof -ti:9630 | xargs kill -9 2>/dev/null || true
    sleep 1
    
    # Cleanup function
    cleanup() {
        echo ""
        print_warning "Shutting down..."
        kill $BACKEND_PID 2>/dev/null || true
        kill $FRONTEND_PID 2>/dev/null || true
        exit 0
    }
    trap cleanup SIGINT SIGTERM
    
    # Start backend server in background
    print_step "Starting Clojure backend on port 3000..."
    clj -M:run &
    BACKEND_PID=$!
    
    # Wait for backend to start
    sleep 5
    
    # Check if backend started successfully
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        print_error "Backend failed to start!"
        exit 1
    fi
    print_success "Backend running (PID: $BACKEND_PID)"
    
    # Start frontend watch in background  
    print_step "Starting shadow-cljs frontend watch..."
    npx shadow-cljs watch app &
    FRONTEND_PID=$!
    
    # Wait for frontend to compile
    sleep 10
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗"
    echo "║              LogicFlow Development Server                   ║"
    echo "╠════════════════════════════════════════════════════════════╣"
    echo "║  🌐 App:        http://localhost:3000                       ║"
    echo "║  🔧 Shadow-cljs: http://localhost:9630                      ║"
    echo "║  📡 WebSocket:  ws://localhost:3000/ws                      ║"
    echo "╠════════════════════════════════════════════════════════════╣"
    echo "║  Press Ctrl+C to stop all servers                          ║"
    echo -e "╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Wait for either process to exit
    wait $BACKEND_PID $FRONTEND_PID
}

cmd_start() {
    print_step "Quick start (backend + frontend)..."
    
    # Kill any existing processes
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    sleep 1
    
    # Cleanup function
    cleanup() {
        echo ""
        print_warning "Stopping servers..."
        jobs -p | xargs kill 2>/dev/null || true
        exit 0
    }
    trap cleanup SIGINT SIGTERM
    
    # Start backend
    print_step "Starting backend..."
    clj -M:run &
    sleep 3
    
    # Start frontend
    print_step "Starting frontend watch..."
    npx shadow-cljs watch app &
    sleep 5
    
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════╗"
    echo "║     🚀 LogicFlow is running!              ║"
    echo "║                                          ║"
    echo "║     Open: http://localhost:3000          ║"
    echo "║                                          ║"
    echo "║     Ctrl+C to stop                       ║"
    echo -e "╚══════════════════════════════════════════╝${NC}"
    echo ""
    
    # Keep running
    wait
}

cmd_run() {
    print_step "Starting LogicFlow..."
    
    # Check if port 3000 is in use
    if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_warning "Port 3000 is in use. Killing existing process..."
        lsof -ti:3000 | xargs kill -9 2>/dev/null || true
        sleep 1
    fi
    
    # Run the application
    clj -M:run "$@"
}

cmd_repl() {
    print_step "Starting REPL..."
    clj -M:dev
}

cmd_test() {
    print_step "Running tests..."
    clj -M:test "$@"
}

cmd_watch() {
    print_step "Starting frontend watch..."
    npx shadow-cljs watch app
}

cmd_release() {
    print_step "Creating release build..."
    
    cmd_clean
    cmd_deps
    
    # Build optimized frontend
    npx shadow-cljs release app
    
    # Create release directory
    RELEASE_DIR="release"
    rm -rf "$RELEASE_DIR"
    mkdir -p "$RELEASE_DIR"
    
    # Copy necessary files
    cp -r src "$RELEASE_DIR/"
    cp -r resources "$RELEASE_DIR/"
    cp deps.edn "$RELEASE_DIR/"
    cp README.md "$RELEASE_DIR/"
    cp LICENSE "$RELEASE_DIR/"
    
    # Create run script
    cat > "$RELEASE_DIR/run.sh" << 'EOF'
#!/bin/bash
cd "$(dirname "${BASH_SOURCE[0]}")"
clj -M -m logicflow.main "$@"
EOF
    chmod +x "$RELEASE_DIR/run.sh"
    
    # Create archive
    tar -czvf "logicflow-release.tar.gz" "$RELEASE_DIR"
    
    print_success "Release created: logicflow-release.tar.gz"
}

cmd_help() {
    echo "Usage: ./build.sh <command> [options]"
    echo ""
    echo "Commands:"
    echo "  check     Check that all dependencies are installed"
    echo "  deps      Install all dependencies (npm + Clojure)"
    echo "  clean     Remove build artifacts"
    echo "  build     Full production build"
    echo "  start     Quick start (backend + frontend together)"
    echo "  dev       Start development environment with full output"
    echo "  run       Run backend only"
    echo "  watch     Start frontend watch only (shadow-cljs)"
    echo "  repl      Start Clojure REPL"
    echo "  test      Run tests"
    echo "  release   Create release archive"
    echo "  help      Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./build.sh start           # Start frontend + backend (recommended)"
    echo "  ./build.sh dev             # Development mode with full logs"
    echo "  ./build.sh run             # Backend only on port 3000"
    echo "  ./build.sh watch           # Frontend only (for separate backend)"
    echo "  ./build.sh check           # Verify dependencies"
    echo ""
}

# ============================================================================
# Main
# ============================================================================

COMMAND="${1:-help}"
shift || true

case "$COMMAND" in
    check)    cmd_check "$@" ;;
    deps)     cmd_deps "$@" ;;
    clean)    cmd_clean "$@" ;;
    build)    cmd_build "$@" ;;
    start)    cmd_start "$@" ;;
    dev)      cmd_dev "$@" ;;
    run)      cmd_run "$@" ;;
    repl)     cmd_repl "$@" ;;
    watch)    cmd_watch "$@" ;;
    test)     cmd_test "$@" ;;
    release)  cmd_release "$@" ;;
    help|--help|-h)
              cmd_help ;;
    *)
        print_error "Unknown command: $COMMAND"
        cmd_help
        exit 1
        ;;
esac

