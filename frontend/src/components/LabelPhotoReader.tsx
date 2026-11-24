import PlaylistAddCheckOutlined from '@mui/icons-material/PlaylistAddCheckOutlined'
import PhotoCameraOutlined from '@mui/icons-material/PhotoCameraOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import LinearProgress from '@mui/material/LinearProgress'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import Typography from '@mui/material/Typography'
import { useRef, useState, type ChangeEvent } from 'react'
import {
  prepareLabelPhoto,
  useExtractLabel,
  useLabelReadingStatus,
  type LabelExtraction,
  type SuggestedAllergen,
} from '../api/labels'
import { toSelection, type AllergenSelection } from '../lib/format'
import { tokens } from '../theme'
import { ErrorNotice } from './ErrorNotice'

const SOURCE_LABEL: Record<SuggestedAllergen['source'], string> = {
  MODEL: 'Read by AI',
  KEYWORD_SCAN: 'Found by keyword check',
  MODEL_AND_KEYWORD_SCAN: 'AI and keyword check agree',
}

function LabelText({ title, text }: { title: string; text: string | undefined }) {
  if (!text) return null
  return (
    <Box>
      <Typography variant="body2" sx={{ fontWeight: 700 }}>
        {title}
      </Typography>
      <Typography
        variant="body2"
        component="blockquote"
        sx={{ m: 0, mt: 0.5, p: 1.25, bgcolor: tokens.canvas, borderLeft: `3px solid ${tokens.line}`, whiteSpace: 'pre-wrap' }}
      >
        {text}
      </Typography>
    </Box>
  )
}

function Review({ result, onApply }: { result: LabelExtraction; onApply: () => void }) {
  if (!result.readable) {
    return (
      <Alert severity="warning">
        The photo couldn’t be read{result.notes ? `: ${result.notes}` : '.'} Take a closer, sharper photo of the
        ingredients and allergen statement, or enter the allergens by hand.
      </Alert>
    )
  }
  return (
    <Box sx={{ display: 'grid', gap: 2 }} aria-label="Label reading">
      <Alert severity="info">
        These are suggestions. Check each one against the label before you save.
      </Alert>
      <LabelText title="Ingredients" text={result.ingredientsText} />
      <LabelText title="Contains statement" text={result.containsStatement} />
      <LabelText title="May contain statement" text={result.mayContainStatement} />
      <Box>
        <Typography variant="body2" sx={{ fontWeight: 700 }}>
          Suggested allergens
        </Typography>
        {result.allergens.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            None found on this label.
          </Typography>
        ) : (
          <List dense disablePadding>
            {result.allergens.map((a) => (
              <ListItem key={a.allergenId} disableGutters sx={{ gap: 1, flexWrap: 'wrap' }}>
                <Chip
                  size="small"
                  label={a.matchType === 'CONTAINS' ? 'Contains' : 'May contain'}
                  sx={{
                    bgcolor: a.matchType === 'CONTAINS' ? tokens.containsTint : tokens.cautionTint,
                    color: a.matchType === 'CONTAINS' ? tokens.contains : tokens.caution,
                    fontWeight: 700,
                  }}
                />
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  {a.displayName}
                </Typography>
                {a.evidence && (
                  <Typography variant="body2" color="text.secondary">
                    “{a.evidence}”
                  </Typography>
                )}
                <Typography variant="body2" color="text.secondary" sx={{ ml: 'auto' }}>
                  {SOURCE_LABEL[a.source]}
                </Typography>
              </ListItem>
            ))}
          </List>
        )}
      </Box>
      {result.unmatchedTerms.length > 0 && (
        <Alert severity="warning">
          Also on the label, but not in our allergen list: {result.unmatchedTerms.join(', ')}. Decide whether any of
          these matter before saving.
        </Alert>
      )}
      <Box>
        <Button variant="outlined" startIcon={<PlaylistAddCheckOutlined />} onClick={onApply}>
          Use these in the allergen editor
        </Button>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
          This replaces the choices in the editor below. Nothing is saved until you save.
        </Typography>
      </Box>
    </Box>
  )
}

/**
 * FR-16 to FR-20: read a label photo with AI and pre-fill the allergen editor. Hidden when
 * label reading is off. The admin still reviews the editor and saves.
 */
export function LabelPhotoReader({
  onApply,
}: {
  onApply: (selection: AllergenSelection, labelExtractionId: string) => void
}) {
  const status = useLabelReadingStatus()
  const extract = useExtractLabel()
  const input = useRef<HTMLInputElement>(null)
  const [applied, setApplied] = useState(false)

  if (!status.isSuccess || !status.data.enabled) return null

  const choose = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file === undefined) return
    setApplied(false)
    extract.mutate(await prepareLabelPhoto(file))
  }

  const apply = (result: LabelExtraction) => {
    onApply(toSelection(result.allergens), result.extractionId)
    setApplied(true)
  }

  return (
    <Box sx={{ border: `1px dashed ${tokens.line}`, borderRadius: 1, p: 2, display: 'grid', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Button
          variant="outlined"
          startIcon={<PhotoCameraOutlined />}
          onClick={() => input.current?.click()}
          disabled={extract.isPending}
        >
          Read allergens from a label photo
        </Button>
        <Typography variant="body2" color="text.secondary">
          Photograph the ingredients and allergen statement.
        </Typography>
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          capture="environment"
          hidden
          aria-label="Label photo"
          onChange={(e) => void choose(e)}
        />
      </Box>
      {extract.isPending && (
        <Box role="status">
          <Typography variant="body2" sx={{ mb: 0.5 }}>
            Reading the label…
          </Typography>
          <LinearProgress />
        </Box>
      )}
      {extract.isError && <ErrorNotice error={extract.error} />}
      {extract.isSuccess && !applied && <Review result={extract.data} onApply={() => apply(extract.data)} />}
      {extract.isSuccess && applied && (
        <Alert severity="success" role="status">
          Suggestions are in the allergen editor. Check them against the label, then save.
        </Alert>
      )}
    </Box>
  )
}
