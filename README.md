# SetlistGPT

An intelligent setlist creator powered by Groq AI and GetSongBPM API. SetlistGPT helps musicians, DJs, and event planners create perfectly curated setlists by analyzing song characteristics, BPM, energy levels, and flow.

## 🎵 About

SetlistGPT combines the power of Groq's advanced language models with GetSongBPM's extensive music database to generate intelligent, data-driven setlists. Whether you're planning a live performance, DJ set, or playlist for an event, SetlistGPT analyzes musical elements to create smooth transitions and optimal energy flow.

## ✨ Features

- **AI-Powered Curation**: Leverage Groq's language models to understand context and preferences
- **BPM Analysis**: Integrate with GetSongBPM API for accurate tempo information
- **Smart Transitions**: Create setlists with smooth tempo and energy transitions
- **Customizable Parameters**: Set preferences for genre, mood, energy level, and duration
- **Data-Driven Decisions**: Make informed choices based on song characteristics

## 🚀 Getting Started

### Prerequisites

- Python 3.8 or higher
- Groq API key
- GetSongBPM API key

### Installation

```bash
# Clone the repository
git clone https://github.com/AndLOLGG/SetlistGPT.git
cd SetlistGPT

# Install dependencies
pip install -r requirements.txt

# Set up your API keys
cp .env.example .env
# Edit .env with your API keys
```

### Usage

```python
# Example usage (to be implemented)
from setlist_gpt import SetlistGenerator

generator = SetlistGenerator()
setlist = generator.create_setlist(
    duration_minutes=60,
    genre="electronic",
    energy_level="high"
)
```

## 📋 How It Works

1. **Input**: Provide your preferences (genre, duration, mood, etc.)
2. **Analysis**: Groq AI processes your requirements and generates song suggestions
3. **BPM Matching**: GetSongBPM API provides tempo data for optimal flow
4. **Generation**: Creates a setlist with intelligent transitions
5. **Output**: Returns a curated setlist ready for your event

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the Erhvervsakademi København License.

## 📧 Contact

For questions or suggestions, please open an issue on GitHub.

## 🔗 Resources

- [Groq AI](https://groq.com/)
- [GetSongBPM API](https://getsongbpm.com/api)

---

## 📁 Project Structure

```
SetlistGPT/
├── README.md
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── dk/ek/setlistgpt/
│   │   │       ├── admin/                 # admin DTOs/controllers
│   │   │       ├── profile/               # Profile entity, controller, service, repo
│   │   │       ├── repertoire/             # Repertoire entity, visibility enum, repository
│   │   │       ├── song/                   # Song entity, repositories
│   │   │       └── (other packages/controllers/services)
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── javascript/
│   │       │   │   ├── frontpage.js
│   │       │   │   ├── create-profile.js
│   │       │   │   └── login-hotfix.js
│   │       │   └── styles.css
│   │       └── templates/
│   │           └── index.html
│   └── test/
│       └── java/                           # unit/integration tests
└── (other files: .github/, .env.example, etc.)

```
