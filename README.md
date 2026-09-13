# CamViewer

Aplicativo Android para visualizar câmeras IP por **RTSP**, com suporte a visualização individual e **MultiView** com várias câmeras na mesma tela.

O projeto foi desenvolvido com foco em uso local, inclusive em **Android TV / Fire TV**, usando controle remoto e navegação por D-pad.

## Recursos

- 📹 Visualização de câmeras IP via RTSP
- 🖥️ Visualização individual em tela cheia
- 🧩 MultiView com várias câmeras simultaneamente
- 🔊 Seleção de áudio no MultiView
- 💾 Cadastro local das câmeras
- 🔐 Suporte a usuário e senha para streams RTSP
- 🚦 Escolha do transporte RTSP: TCP ou UDP
- ⭐ Definição de uma MultiView para iniciar automaticamente
- 🎮 Navegação por controle remoto / D-pad
- 📺 Compatibilidade com Android TV e Fire TV
- 🌙 Interface escura, adequada para visualização em TV
- 🔄 Ao sair do aplicativo, a tarefa é removida para que uma nova abertura seja iniciada do zero

## Como usar

1. Abra o CamViewer.
2. Cadastre uma câmera informando:
   - Nome
   - URL RTSP
   - Usuário, se necessário
   - Senha, se necessário
   - Transporte RTSP (TCP ou UDP)
3. Abra a câmera para visualizar o stream.
4. Para visualizar várias câmeras ao mesmo tempo, crie uma **MultiView** e selecione as câmeras desejadas.
5. Uma MultiView pode ser definida como tela inicial do aplicativo.

### Exemplo de URL RTSP

```text
rtsp://192.168.1.100:554/stream
```

> A URL exata depende do modelo e da configuração da câmera.

## Android TV / Fire TV

O aplicativo possui suporte a dispositivos com Android TV e Fire TV. A interface foi preparada para navegação usando as setas, OK e voltar do controle remoto, sem depender de tela sensível ao toque.

## Requisitos

- Android compatível com o aplicativo
- Rede local com acesso às câmeras IP
- Câmeras com suporte a RTSP
- Para Android TV / Fire TV, o dispositivo precisa permitir a instalação de aplicativos Android compatíveis

## Privacidade e segurança

As configurações das câmeras são armazenadas localmente no dispositivo. As URLs RTSP podem conter credenciais, portanto **não compartilhe o arquivo de configuração ou capturas de tela que exponham usuário e senha**.

O aplicativo foi projetado para acessar os streams diretamente pela rede local.

## Tecnologia

- Kotlin
- Android SDK
- AndroidX
- Jetpack Media3 / ExoPlayer
- RTSP

## Estrutura do projeto

```text
CamViewer/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           ├── res/
│           └── AndroidManifest.xml
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Build

O projeto pode ser compilado pelo Android Studio ou pelo Gradle/CI configurado no repositório.

O APK gerado pode ser instalado manualmente em dispositivos Android compatíveis.

## Aviso

Este é um projeto pessoal. A compatibilidade com câmeras pode variar conforme o fabricante, codec utilizado, formato da URL RTSP e configuração da rede.
