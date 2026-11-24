import SaveOutlined from '@mui/icons-material/SaveOutlined'
import EditOutlined from '@mui/icons-material/EditOutlined'
import DeleteOutlined from '@mui/icons-material/DeleteOutlined'
import PersonAddAlt1Outlined from '@mui/icons-material/PersonAddAlt1Outlined'
import LoginOutlined from '@mui/icons-material/LoginOutlined'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { initials } from '../lib/format'
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
import { tokens } from '../theme'

/** "Child 1" -> "C1", "Maya" -> "M": tells children apart at a glance. */
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

  const dirty = selected.size !== profile.avoidList.length || profile.avoidList.some((id) => !selected.has(id))

  const submitAvoidList = (event: FormEvent) => {
    event.preventDefault()
    save.mutate({ id: profile.id, allergenIds: [...selected].sort() })
  }

  const submitLabel = (event: FormEvent) => {
    event.preventDefault()
    rename.mutate({ id: profile.id, label })
  }

  return (
    <Paper component="li" className="profile-card" sx={{ p: { xs: 2, md: 3 }, display: 'grid', gap: 2 }}>
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
        <Avatar aria-hidden="true" sx={{ bgcolor: tokens.sageTint, color: tokens.sage, fontWeight: 700, fontSize: '0.9375rem' }}>
          {initials(profile.label)}
        </Avatar>
        <Box>
          <Typography variant="h3">{profile.label}</Typography>
          <Typography variant="body2" color="text.secondary">
            {summary(profile, allergens)}
          </Typography>
        </Box>
      </Box>

      <Box component="form" onSubmit={submitAvoidList} aria-label={`Avoid list for ${profile.label}`}>
        <Box component="fieldset" sx={{ border: 0, p: 0, m: 0 }}>
          <Typography component="legend" variant="h4" sx={{ mb: 1 }}>
            Allergens to avoid for {profile.label}
          </Typography>
          <AvoidListPicker idPrefix={profile.id} allergens={allergens} selected={selected} onChange={setSelected} />
        </Box>
        {save.isError && <ErrorNotice error={save.error} />}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 2 }}>
          <Button type="submit" variant="contained" startIcon={<SaveOutlined />} disabled={!dirty || save.isPending}>
            Save avoid list
          </Button>
          {dirty && (
            <Typography variant="body2" color="text.secondary">
              Unsaved changes
            </Typography>
          )}
        </Box>
      </Box>

      <Box
        component="details"
        sx={{ borderTop: `1px solid ${tokens.line}`, pt: 1.5, '& summary': { cursor: 'pointer', color: tokens.muted } }}
      >
        <Typography component="summary" variant="body2">
          Rename or delete
        </Typography>
        <Box component="form" onSubmit={submitLabel} sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1.5 }}>
          <TextField
            id={`label-${profile.id}`}
            label="Label"
            size="small"
            required
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 40 } }}
          />
          <Button type="submit" variant="outlined" startIcon={<EditOutlined />} disabled={rename.isPending || label.trim() === profile.label}>
            Rename
          </Button>
        </Box>
        {rename.isError && <ErrorNotice error={rename.error} />}
        <Box sx={{ mt: 1.5 }}>
          {!confirmDelete ? (
            <Button color="error" startIcon={<DeleteOutlined />} onClick={() => setConfirmDelete(true)}>
              Delete {profile.label}
            </Button>
          ) : (
            <Alert
              severity="warning"
              action={
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button color="error" variant="contained" size="small" onClick={() => remove.mutate(profile.id)} disabled={remove.isPending}>
                    Yes, delete
                  </Button>
                  <Button size="small" color="inherit" onClick={() => setConfirmDelete(false)}>
                    Cancel
                  </Button>
                </Box>
              }
            >
              Delete {profile.label} and their avoid list?
            </Alert>
          )}
        </Box>
        {remove.isError && <ErrorNotice error={remove.error} />}
      </Box>
    </Paper>
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

  if (me.isPending) return <Typography>Loading…</Typography>
  if (!me.data?.authenticated) {
    return (
      <Paper sx={{ p: 3, maxWidth: 560 }}>
        <Typography variant="h2">My children</Typography>
        <Typography sx={{ my: 1.5 }}>Sign in to set up each child's avoid list.</Typography>
        <Button variant="contained" startIcon={<LoginOutlined />} onClick={() => signIn(me.data)}>
          Sign in
        </Button>
      </Paper>
    )
  }
  if (!isShopper) {
    return (
      <Box>
        <Typography variant="h2">My children</Typography>
        <Alert severity="info" sx={{ mt: 2 }}>
          This account is not a shopper account.
        </Alert>
      </Box>
    )
  }

  const submitNew = (event: FormEvent) => {
    event.preventDefault()
    create.mutate(newLabel, { onSuccess: () => setNewLabel('') })
  }

  return (
    <Box component="section" aria-labelledby="profiles-heading" sx={{ maxWidth: 880 }}>
      <Typography variant="h2" id="profiles-heading">
        My children
      </Typography>
      <Typography color="text.secondary" sx={{ mt: 0.5, mb: 3, maxWidth: '62ch' }}>
        Add a profile for each child and choose what they must avoid. A label like “Child 1” is enough; we don't
        need names or birthdays.
      </Typography>

      <Paper component="form" onSubmit={submitNew} sx={{ p: 2, display: 'flex', flexWrap: 'wrap', gap: 1, mb: 3 }}>
        <TextField
          id="new-profile"
          label="New profile label"
          placeholder="Child 1"
          size="small"
          required
          value={newLabel}
          onChange={(e) => setNewLabel(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 40 } }}
          sx={{ flex: '1 1 220px' }}
        />
        <Button type="submit" variant="contained" startIcon={<PersonAddAlt1Outlined />} disabled={create.isPending}>
          Add profile
        </Button>
      </Paper>
      {create.isError && <ErrorNotice error={create.error} />}

      {(profiles.isPending || allergens.isPending) && <Typography>Loading…</Typography>}
      {profiles.isError && <ErrorNotice error={profiles.error} />}
      {profiles.isSuccess && allergens.isSuccess && profiles.data.length === 0 && (
        <Typography color="text.secondary">No profiles yet. Add one above to start checking products.</Typography>
      )}
      {profiles.isSuccess && allergens.isSuccess && (
        <Box component="ul" className="profile-list" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 2 }}>
          {profiles.data.map((profile) => (
            <ProfileCard
              key={`${profile.id}:${profile.avoidListVersion}:${profile.label}`}
              profile={profile}
              allergens={allergens.data}
            />
          ))}
        </Box>
      )}
    </Box>
  )
}
