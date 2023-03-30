import React, { useState } from "react";
import "./App.css";
import SignupForm from "./SignupForm";

function App() {
  const [message, setMessage] = useState("");
  const [page, setPage] = useState("hello");

  const handleClick = async () => {
    const response = await fetch("http://localhost:8080/hello");
    const data = await response.text();
    setMessage(data);
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
  };

  return (
    <div className="App">
      <div className="Menu">
        <ul>
          <li onClick={() => handlePageChange("hello")}>Hello World</li>
          <li onClick={() => handlePageChange("signup")}>Sign Up</li>
        </ul>
      </div>
      <div className="TopBar">
        <h1>My App</h1>
      </div>
      <div className="Content">
        {page === "hello" && (
          <>
            <h1>Hello World</h1>
            <button onClick={handleClick}>Click me</button>
            {message && <p>{message}</p>}
          </>
        )}
        {page === "signup" && <SignupForm />}
      </div>
    </div>
  );
}

export default App;
