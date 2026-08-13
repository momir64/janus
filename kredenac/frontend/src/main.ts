import './style.css'

const app = document.querySelector<HTMLDivElement>('#app')!

app.innerHTML = `
  <h1>Kredenac backend test</h1>
  <form id="register-start-form">
    <input id="username" type="text" placeholder="username" required />
    <input id="email" type="email" placeholder="email" required />
    <button type="submit">POST /api/webauthn/register/start</button>
  </form>
  <pre id="result"></pre>
`

const form = document.querySelector<HTMLFormElement>('#register-start-form')!
const result = document.querySelector<HTMLPreElement>('#result')!

form.addEventListener('submit', async (event) => {
  event.preventDefault()

  const username = document.querySelector<HTMLInputElement>('#username')!.value
  const email = document.querySelector<HTMLInputElement>('#email')!.value

  result.textContent = 'Loading...'

  try {
    const response = await fetch('/api/webauthn/register/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email })
    })

    const data = await response.json()
    result.textContent = `${response.status}\n${JSON.stringify(data, null, 2)}`
  } catch (error) {
    result.textContent = `Request failed: ${error}`
  }
})
