# LangMod: says item names with the voices installed in Windows (Settings > Time & language > Speech).
# The game starts this with Windows PowerShell 5.1 and keeps it running. It uses the WinRT speech API,
# which sees every installed voice. One request per line on stdin, answers on stdout; text, voice ids
# and names travel in base64 (UTF-8), so the pipes only ever carry ASCII.
#   voices                         -> one "voice <id> <name> <language> <gender>" line per voice, then "end"
#   say <voice id> <speed> <text>  -> "wav <the WAV file in base64>"
# A request that fails is answered with "error <message>".
$ErrorActionPreference = 'Stop'

function Encode([string] $text) { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($text)) }
function Decode([string] $text) { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($text)) }
function Answer([string] $line) { [Console]::Out.WriteLine($line); [Console]::Out.Flush() }

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Foundation, ContentType = WindowsRuntime]
    $null = [Windows.Media.SpeechSynthesis.SpeechSynthesisStream, Windows.Foundation, ContentType = WindowsRuntime]
    # WinRT's async calls become .NET tasks through AsTask(IAsyncOperation<T>).
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    $asStreamTask = $asTask.MakeGenericMethod([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
    $synthesizer = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
} catch {
    Answer ('error ' + (Encode $_.Exception.Message))
    exit 1
}

Answer 'ready'

while ($true) {
    $line = [Console]::In.ReadLine()

    if ($null -eq $line) {
        break
    }

    $parts = $line.Split(' ')

    try {
        switch ($parts[0]) {
            'voices' {
                foreach ($voice in [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices) {
                    Answer ('voice ' + (Encode $voice.Id) + ' ' + (Encode $voice.DisplayName) + ' ' + $voice.Language + ' ' + $voice.Gender)
                }

                Answer 'end'
            }
            'say' {
                $id = Decode $parts[1]
                $voice = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices | Where-Object { $_.Id -eq $id } | Select-Object -First 1

                if ($null -eq $voice) {
                    throw "The voice $id is not installed"
                }

                $synthesizer.Voice = $voice

                try {
                    # Windows 10 1803 and later.
                    $synthesizer.Options.SpeakingRate = [double]::Parse($parts[2], [Globalization.CultureInfo]::InvariantCulture)
                } catch {
                }

                $task = $asStreamTask.Invoke($null, @($synthesizer.SynthesizeTextToStreamAsync((Decode $parts[3]))))
                $null = $task.Wait(-1)
                $speech = $task.Result
                $source = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($speech)
                $wav = New-Object System.IO.MemoryStream
                $source.CopyTo($wav)
                $source.Dispose()
                $speech.Dispose()
                Answer ('wav ' + [Convert]::ToBase64String($wav.ToArray()))
            }
            default {
                throw "Unknown request: $($parts[0])"
            }
        }
    } catch {
        $message = $_.Exception.Message

        if ($_.Exception.InnerException) {
            $message = $_.Exception.InnerException.Message
        }

        Answer ('error ' + (Encode $message))
    }
}
