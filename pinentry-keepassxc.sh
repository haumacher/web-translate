#!/bin/sh
# pinentry-Ersatz: holt die GPG-Passphrase aus KeePassXC.
#
# gpg-agent nennt per SETKEYINFO den Keygrip des Schluessels. Daraus
# ermittelt das Skript die primaere Key-ID und sucht in KeePassXC den
# Eintrag namens  "GPG-Key 0x<KEYID>"  -- dessen Passwort-Feld enthaelt
# die Passphrase. So bedient ein Skript beliebig viele Schluessel.
#
# Das Master-Passwort wird per pinentry-gnome3 abgefragt (laeuft ueber
# D-Bus, braucht kein $DISPLAY). Schlaegt der KeePassXC-Abruf fehl oder
# meldet gpg eine falsche Passphrase, wird direkt im Fenster gefragt.
#
# Log unter $LOG (nur Laengen, keine Passwoerter).

# ----- Konfiguration -------------------------------------------------------
KP_CLI=/usr/local/bin/keepassxc-cli
KP_DB="$HOME/private/Documents/addresses.kdbx"
GUI_PINENTRY=/usr/bin/pinentry-gnome3
GPG=/usr/bin/gpg
LOG="$HOME/.gnupg/pinentry-keepassxc.log"
# ---------------------------------------------------------------------------

log() { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*" >> "$LOG" 2>/dev/null; }

OPTIONS=""   # OPTION-Zeilen von gpg-agent (werden ans Fenster weitergereicht)
KEYGRIP=""   # Keygrip aus SETKEYINFO
RETRY=0      # 1, sobald gpg eine fehlerhafte Passphrase gemeldet hat
MASTER=""    # einmal abgefragtes KeePassXC-Master-Passwort

# GUI-Abfrage. $1=Beschreibung $2=Prompt -> Wert auf stdout (leer = Abbruch).
ask() {
    {
        printf '%s' "$OPTIONS"
        printf 'SETTITLE GPG\n'
        printf 'SETDESC %s\n' "$1"
        printf 'SETPROMPT %s\n' "$2"
        printf 'GETPIN\nBYE\n'
    } | "$GUI_PINENTRY" 2>>"$LOG" \
      | sed -n 's/^D //p' \
      | sed 's/%0A/\n/g; s/%0D/\r/g; s/%25/%/g'
}

# Keygrip -> primaere Key-ID (ueber den oeffentlichen Schluesselbund).
keyid_for_grip() {
    [ -n "$1" ] || return
    "$GPG" --list-keys --with-keygrip --with-colons 2>>"$LOG" \
        | awk -F: -v g="$1" '$1=="pub"{k=$5} $1=="grp"&&$10==g{print k;exit}'
}

# GPG-Passphrase ermitteln.
get_passphrase() {
    if [ "$RETRY" -eq 0 ]; then
        keyid=$(keyid_for_grip "$KEYGRIP")
        if [ -n "$keyid" ]; then
            entry="GPG-Key 0x$keyid"
            log "Keygrip $KEYGRIP -> Eintrag \"$entry\""
            [ -n "$MASTER" ] || MASTER=$(ask \
                "KeePassXC-Master-Passwort eingeben, um die Passphrase fuer 0x$keyid abzurufen." \
                "Master-Passwort:")
            if [ -n "$MASTER" ]; then
                pass=$(printf '%s\n' "$MASTER" \
                    | "$KP_CLI" show -s -a Password "$KP_DB" "$entry" 2>>"$LOG")
                rc=$?
                log "keepassxc-cli rc=$rc, Passphrase: ${#pass} Zeichen"
                if [ -n "$pass" ]; then
                    printf '%s' "$pass"
                    return
                fi
            fi
        else
            log "keine Key-ID zu Keygrip [$KEYGRIP] -- ueberspringe KeePassXC"
        fi
    fi
    log "Fallback: frage GPG-Passphrase direkt ab"
    ask "GPG-Passphrase eingeben (KeePassXC-Abruf nicht moeglich)." "Passphrase:"
}

log "--- pinentry-keepassxc gestartet ---"
printf 'OK Pleased to meet you\n'
PASS=""
while IFS= read -r line; do
    log "<< $line"
    case "$line" in
        'OPTION '*)
            OPTIONS="${OPTIONS}${line}
"
            printf 'OK\n'
            ;;
        'SETKEYINFO '*)
            ki=${line#SETKEYINFO }
            case "$ki" in
                */*) KEYGRIP=${ki#*/} ;;
                *)   KEYGRIP="" ;;
            esac
            printf 'OK\n'
            ;;
        SETERROR*)
            RETRY=1
            PASS=""
            printf 'OK\n'
            ;;
        GETPIN*)
            [ -n "$PASS" ] || PASS=$(get_passphrase)
            if [ -n "$PASS" ]; then
                log "liefere Passphrase an gpg (${#PASS} Zeichen)"
                printf 'D %s\n' "$(printf '%s' "$PASS" | sed 's/%/%25/g')"
                printf 'OK\n'
            else
                log "keine Passphrase -- melde ERR cancelled"
                printf 'ERR 83886179 Operation cancelled\n'
            fi
            ;;
        BYE*)
            printf 'OK closing connection\n'
            exit 0
            ;;
        *)
            printf 'OK\n'
            ;;
    esac
done
