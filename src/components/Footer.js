import React from 'react'
import { ReactComponent as Github } from '../assets/github.svg'

function Footer() {
  return (
    <footer className='footer'>
      Copyright © 2024 Vineet
      <a href='https://github.com/Vineet829' target='_blank' rel='noreferrer'>
        <Github className='icon' />
      </a>
    </footer>
  )
}

export default Footer
