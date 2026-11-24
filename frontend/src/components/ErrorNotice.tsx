import { ApiError } from '../api/client'

/** Readable error text for failed API calls. */
export function ErrorNotice({ error }: { error: unknown }) {
  let message = 'Something went wrong. Please try again.'
  if (error instanceof ApiError) {
    if (error.status === 401) message = 'Please sign in to continue.'
    else if (error.status === 403) message = 'You do not have access to this page.'
    else if (error.status === 404) message = 'Not found.'
    else message = error.message
  }
  return (
    <p className="problem" role="alert">
      <span aria-hidden="true">⚠ </span>
      {message}
    </p>
  )
}
