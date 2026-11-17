package com.vocabulary.reference.infrastructure;

import com.vocabulary.reference.application.SpeechFallback;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Existing Windows voices are a labelled fallback, not presented as natural recordings. */
public final class WindowsSpeechFallback implements SpeechFallback {
    // All user text travels on standard input. It is never interpolated into shell code.
    private static final String SCRIPT = "$ErrorActionPreference='Stop'; "
            + "[Console]::InputEncoding=[System.Text.Encoding]::UTF8; "
            + "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
            + "$accent=[Console]::ReadLine(); $word=[Console]::ReadLine(); "
            + "Add-Type -AssemblyName System.Speech; "
            + "$synth=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            + "try { $voice=$synth.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq $accent } | Select-Object -First 1; "
            + "if ($null -eq $voice) { throw ('No installed '+$accent+' voice.'); }; "
            + "$synth.SelectVoice($voice.VoiceInfo.Name); $synth.Rate=0; "
            + "[Console]::WriteLine($voice.VoiceInfo.Name); $synth.Speak($word); } finally { $synth.Dispose(); }";
    private Process current;

    @Override public String speak(String word, String accent, BooleanSupplier cancelled) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IOException("Local speech fallback is available only on Windows.");
        }
        if (cancelled.getAsBoolean()) return "Stopped";
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-Command", SCRIPT).redirectErrorStream(true).start();
        synchronized (this) {
            if (cancelled.getAsBoolean()) { process.destroyForcibly(); return "Stopped"; }
            current = process;
        }
        try {
            try (OutputStreamWriter input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                input.write(accent); input.write('\n'); input.write(word); input.write('\n');
            }
            if (!process.waitFor(12, TimeUnit.SECONDS)) throw new IOException("The local speech engine timed out.");
            String output = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) throw new IOException("No usable " + accent + " voice is installed.");
            return output.isBlank() ? "Windows voice" : output.lines().findFirst().orElse("Windows voice");
        } finally {
            synchronized (this) { if (current == process) current = null; }
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Override public void stop() {
        Process previous;
        synchronized (this) { previous = current; current = null; }
        if (previous != null && previous.isAlive()) previous.destroyForcibly();
    }
}
