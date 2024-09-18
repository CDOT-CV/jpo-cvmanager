import React, { useState } from 'react'
import { Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'

import 'react-widgets/styles.css'
import RsuApi from '../apis/rsu-api'

import './css/ContactSupportMenu.css'
import toast from 'react-hot-toast'
import Dialog from '@mui/material/Dialog'
import { DialogActions, DialogContent, DialogTitle } from '@mui/material'

const ContactSupportMenu = () => {
  const [hidden, setHidden] = useState(true) // hidden by default
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm()

  const onSubmit = async (data: Object) => {
    try {
      const res = await RsuApi.postContactSupport(data)
      const status = res.status
      if (status === 200) {
        toast.success('Successfully sent email')
        reset()
      } else {
        toast.error('Something went wrong: ' + status)
      }
    } catch (exception_var) {
      toast.error('An exception occurred, please try again later')
    }
    setHidden(true)
  }

  if (hidden) {
    return (
      <div>
        <button
          type="button"
          className="showbutton"
          onClick={() => {
            setHidden(!hidden)
          }}
        >
          Contact Support
        </button>
      </div>
    )
  }

  return (
    <Dialog open={true}>
      <DialogTitle>Contact Support</DialogTitle>
      <DialogContent>
        <Form
          id="contact-support-form"
          onSubmit={handleSubmit(onSubmit)}
          style={{ fontFamily: 'Arial, Helvetica, sans-serif' }}
        >
          <Form.Group className="mb-3" controlId="email">
            <Form.Label className="label">Your Email</Form.Label>
            <Form.Control
              type="email"
              placeholder="Enter your email (Required)"
              {...register('email', {
                required: 'Email is required',
              })}
            />
            {errors.email && <Form.Text className="text-danger">{errors.email.message}</Form.Text>}
          </Form.Group>
          <Form.Group className="mb-3" controlId="subject">
            <Form.Label className="label">Subject</Form.Label>
            <Form.Control
              type="text"
              placeholder="Enter your subject (Required)"
              {...register('subject', {
                required: 'Subject is required',
              })}
            />
            {errors.subject && <Form.Text className="text-danger">{errors.subject.message}</Form.Text>}
          </Form.Group>
          <Form.Group className="mb-3" controlId="message">
            <Form.Label className="label">Message</Form.Label>
            <Form.Control
              as="textarea"
              rows={5}
              placeholder="Enter your message (Required)"
              {...register('message', {
                required: 'Message is required',
              })}
            />
            {errors.message && <Form.Text className="text-danger">{errors.message.message}</Form.Text>}
          </Form.Group>
        </Form>
      </DialogContent>
      <DialogActions>
        <button
          onClick={() => {
            setHidden(!hidden)
          }}
          className="admin-button"
        >
          Close
        </button>
        <button form="contact-support-form" type="submit" className="admin-button">
          Send Email
        </button>
      </DialogActions>
    </Dialog>
  )
}

export default ContactSupportMenu
