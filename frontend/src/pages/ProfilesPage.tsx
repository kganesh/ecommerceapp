import { useState, type FormEvent } from 'react'
import { useAllergens } from '../api/catalog'
import type { Allergen } from '../api/client'
import {
  useCreateProfile,
  useDeleteProfile,
  useProfiles,
  useRenameProfile,
  useSetAvoidList,
  type Profile,
} from '../api/profiles'
import { hasRole, signIn, useMe } from '../api/session'
import { AvoidListPicker } from '../components/AvoidListPicker'
import { ErrorNotice } from '../components/ErrorNotice'

function summary(profile: Profile, allergens: Allergen[]): string {
  if (profile.avoidList.length === 0) return 'Avoiding nothing yet.'
  const names = new Map(allergens.map((a) => [a.id, a.displayName]))
  return `Avoiding: ${profile.avoidList.map((id) => names.get(id) ?? id).join(', ')}.`
}

/** Keyed by profile id + avoid-list version, so local edits start from what is saved. */
function ProfileCard({ profile, allergens }: { profile: Profile; allergens: Allergen[] }) {
  const rename = useRenameProfile()
  const remove = useDeleteProfile()
  const save = useSetAvoidList()
  const [label, setLabel] = useState(profile.label)
  const [selected, setSelected] = useState<Set<string>>(() => new Set(profile.avoidList))
  const [confirmDelete, setConfirmDelete] = useState(false)

  const dirty =
    selected.size !== profile.avoidList.length || profile.avoidList.some((id) => !selected.has(id))

  const submitAvoidList = (event: FormEvent) => {
    event.preventDefault()
    save.mutate({ id: profile.id, allergenIds: [...selected].sort() })
  }

  const submitLabel = (event: FormEvent) => {
    event.preventDefault()
    rename.mutate({ id: profile.id, label })
  }

  return (
    <li className="profile-card">
      <h3>{profile.label}</h3>
      <p>{summary(profile, allergens)}</p>

      <form onSubmit={submitAvoidList} aria-label={`Avoid list for ${profile.label}`}>
        <fieldset>
          <legend>Allergens to avoid for {profile.label}</legend>
          <AvoidListPicker
            idPrefix={profile.id}
            allergens={allergens}
            selected={selected}
            onChange={setSelected}
          />
        </fieldset>
        {save.isError && <ErrorNotice error={save.error} />}
        <button type="submit" disabled={!dirty || save.isPending}>
          Save avoid list
        </button>
        {dirty && <span className="hint"> Unsaved changes</span>}
      </form>

      <details>
        <summary>Rename or delete</summary>
        <form onSubmit={submitLabel} className="inline">
          <label htmlFor={`label-${profile.id}`}>Label</label>
          <input
            id={`label-${profile.id}`}
            maxLength={40}
            required
            value={label}
            onChange={(e) => setLabel(e.target.value)}
          />
          <button type="submit" disabled={rename.isPending || label.trim() === profile.label}>
            Rename
          </button>
        </form>
        {rename.isError && <ErrorNotice error={rename.error} />}
        {!confirmDelete ? (
          <button type="button" onClick={() => setConfirmDelete(true)}>
            Delete {profile.label}
          </button>
        ) : (
          <p>
            Delete {profile.label} and their avoid list?{' '}
            <button type="button" onClick={() => remove.mutate(profile.id)} disabled={remove.isPending}>
              Yes, delete
            </button>{' '}
            <button type="button" onClick={() => setConfirmDelete(false)}>
              Cancel
            </button>
          </p>
        )}
        {remove.isError && <ErrorNotice error={remove.error} />}
      </details>
    </li>
  )
}

/** FR-2, FR-3: a parent's child profiles and their avoid lists. */
export function ProfilesPage() {
  const me = useMe()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const profiles = useProfiles(isShopper)
  const allergens = useAllergens()
  const create = useCreateProfile()
  const [newLabel, setNewLabel] = useState('')

  if (me.isPending) return <p>Loading…</p>
  if (!me.data?.authenticated) {
    return (
      <section>
        <h2>My children</h2>
        <p>Sign in to set up each child's avoid list.</p>
        <button type="button" onClick={() => signIn(me.data)}>
          Sign in
        </button>
      </section>
    )
  }
  if (!isShopper) {
    return (
      <section>
        <h2>My children</h2>
        <p className="problem">This account is not a shopper account.</p>
      </section>
    )
  }

  const submitNew = (event: FormEvent) => {
    event.preventDefault()
    create.mutate(newLabel, { onSuccess: () => setNewLabel('') })
  }

  return (
    <section aria-labelledby="profiles-heading">
      <h2 id="profiles-heading">My children</h2>
      <p>
        Add a profile for each child and choose what they must avoid. Use a label like “Child 1”; we don't need
        names or birthdays.
      </p>

      <form onSubmit={submitNew} className="inline">
        <label htmlFor="new-profile">New profile label</label>
        <input
          id="new-profile"
          maxLength={40}
          required
          placeholder="Child 1"
          value={newLabel}
          onChange={(e) => setNewLabel(e.target.value)}
        />
        <button type="submit" disabled={create.isPending}>
          Add profile
        </button>
      </form>
      {create.isError && <ErrorNotice error={create.error} />}

      {(profiles.isPending || allergens.isPending) && <p>Loading…</p>}
      {profiles.isError && <ErrorNotice error={profiles.error} />}
      {profiles.isSuccess && allergens.isSuccess && profiles.data.length === 0 && <p>No profiles yet.</p>}
      {profiles.isSuccess && allergens.isSuccess && (
        <ul className="profile-list">
          {profiles.data.map((profile) => (
            <ProfileCard
              key={`${profile.id}:${profile.avoidListVersion}:${profile.label}`}
              profile={profile}
              allergens={allergens.data}
            />
          ))}
        </ul>
      )}
    </section>
  )
}
