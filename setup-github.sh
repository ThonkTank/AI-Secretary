#!/bin/bash
# AI Secretary - GitHub Setup Script
# Dieses Script hilft beim Einrichten des GitHub Repositories

set -e  # Bei Fehler stoppen

echo "=========================================="
echo "   AI Secretary - GitHub Setup"
echo "=========================================="
echo ""

# Farben für Output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Funktion für Erfolgsmeldungen
success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Funktion für Warnungen
warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Funktion für Fehler
error() {
    echo -e "${RED}✗${NC} $1"
}

# Prüfen, ob git installiert ist
if ! command -v git &> /dev/null; then
    error "Git ist nicht installiert!"
    echo "Installiere mit: pkg install git"
    exit 1
fi
success "Git ist installiert"

# Prüfen, ob wir in einem Git-Repository sind
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    error "Kein Git-Repository gefunden!"
    echo "Initialisiere Repository mit: git init"
    exit 1
fi
success "Git-Repository gefunden"

# Prüfen, ob es uncommitted changes gibt
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    warning "Es gibt uncommitted changes!"
    echo "Bitte committen Sie zuerst alle Änderungen."
    git status --short
    exit 1
fi
success "Alle Änderungen sind committed"

echo ""
echo "=========================================="
echo "   GitHub Repository Setup"
echo "=========================================="
echo ""

# Prüfen, ob Remote bereits existiert
if git remote | grep -q "origin"; then
    echo "Remote 'origin' existiert bereits:"
    git remote get-url origin
    echo ""
    read -p "Remote ändern? (y/N): " -n 1 -r
    echo
    if [[ $REPL =~ ^[Yy]$ ]]; then
        git remote remove origin
        success "Remote 'origin' entfernt"
    else
        warning "Verwende existierenden Remote"
    fi
fi

# Wenn kein Remote existiert, fragen
if ! git remote | grep -q "origin"; then
    echo ""
    echo "Bitte erstellen Sie zuerst ein GitHub Repository:"
    echo "  1. Gehen Sie zu: https://github.com/new"
    echo "  2. Repository Name: ai-secretary-native"
    echo "  3. Description: AI Secretary - Native Android Task Management App"
    echo "  4. Visibility: Public oder Private"
    echo "  5. NICHT initialisieren mit README/LICENSE (leeres Repo)"
    echo ""
    read -p "Repository erstellt? (Enter zum Fortfahren)"
    echo ""

    # GitHub Username abfragen
    read -p "Ihr GitHub Username: " GITHUB_USER

    if [ -z "$GITHUB_USER" ]; then
        error "Username ist erforderlich!"
        exit 1
    fi

    # Repository URL zusammenbauen
    REPO_URL="https://github.com/$GITHUB_USER/ai-secretary-native.git"

    echo ""
    echo "Repository URL: $REPO_URL"
    read -p "Korrekt? (Y/n): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Nn]$ ]]; then
        read -p "Bitte geben Sie die vollständige Repository-URL ein: " REPO_URL
    fi

    # Remote hinzufügen
    git remote add origin "$REPO_URL"
    success "Remote 'origin' hinzugefügt: $REPO_URL"
fi

echo ""
echo "=========================================="
echo "   Branch konfigurieren"
echo "=========================================="
echo ""

# Aktuellen Branch ermitteln
CURRENT_BRANCH=$(git branch --show-current)
echo "Aktueller Branch: $CURRENT_BRANCH"

# Zu 'main' umbenennen falls nötig
if [ "$CURRENT_BRANCH" != "main" ]; then
    read -p "Branch zu 'main' umbenennen? (Y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Nn]$ ]]; then
        git branch -M main
        CURRENT_BRANCH="main"
        success "Branch umbenannt zu 'main'"
    fi
fi

echo ""
echo "=========================================="
echo "   Push zu GitHub"
echo "=========================================="
echo ""

# Commits anzeigen
echo "Commits zum Pushen:"
git log --oneline -5
echo ""

read -p "Jetzt zu GitHub pushen? (Y/n): " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    echo ""
    echo "Pushing zu GitHub..."

    if git push -u origin "$CURRENT_BRANCH"; then
        echo ""
        success "✨ Erfolgreich zu GitHub gepusht! ✨"
        echo ""
        echo "Nächste Schritte:"
        echo "  1. Gehen Sie zu: https://github.com/$GITHUB_USER/ai-secretary-native"
        echo "  2. Klicken Sie auf 'Actions' Tab"
        echo "  3. Warten Sie, bis der Build abgeschlossen ist (~5 Minuten)"
        echo "  4. Laden Sie das 'app-debug' Artifact herunter"
        echo "  5. Entpacken Sie die ZIP → app-debug.apk"
        echo "  6. Installieren Sie mit: adb install app-debug.apk"
        echo ""
        success "Build-System ist produktionsreif!"
    else
        error "Push fehlgeschlagen!"
        echo ""
        echo "Mögliche Gründe:"
        echo "  - Keine Berechtigung (GitHub Token/SSH-Key erforderlich)"
        echo "  - Remote-Repository existiert nicht"
        echo "  - Branch-Konflikt"
        echo ""
        echo "Für Authentifizierung siehe:"
        echo "  https://docs.github.com/en/authentication"
    fi
else
    warning "Push abgebrochen"
    echo ""
    echo "Manuell pushen mit:"
    echo "  git push -u origin $CURRENT_BRANCH"
fi

echo ""
echo "=========================================="
echo "   Setup abgeschlossen"
echo "=========================================="
